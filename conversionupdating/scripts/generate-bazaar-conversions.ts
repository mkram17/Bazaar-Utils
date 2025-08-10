import * as fs from 'fs';
import * as path from 'path';
import { startCase, toLower } from 'lodash';
import romans from 'romans';

const ITEMS_API_URL = 'https://api.hypixel.net/v2/resources/skyblock/items';
const BAZAAR_API_URL = 'https://api.hypixel.net/v2/skyblock/bazaar';

const OUTPUT_FILE_NAME = 'bazaar-conversions.json';
const OUTPUT_PATH = path.join(process.cwd(), '..', OUTPUT_FILE_NAME);

interface SkyBlockItem {
    id: string;
    name: string | null;
    [k: string]: any;
}
interface SkyBlockItemsApiResponse {
    success: true;
    lastUpdated: number;
    items: SkyBlockItem[];
}
interface BazaarApiResponse {
    success: true;
    lastUpdated: number;
    products: Record<string, unknown>;
}
interface ApiErrorResponse {
    success: false;
    cause?: string;
}

const ENDS_WITH_NUMBER = /\d$/;
const COLOR_CODE_PATTERN = /§[0-9A-FK-ORa-fk-or]/g;
const PLACEHOLDER_PATTERN = /%%\w+%%/g;

// Manual overrides for awkward IDs whose official item name might be null or undesirable
const NAME_OVERRIDES: Record<string, string> = {
    // "ENCHANTMENT_COMPACT_1": "Compact I",
    // Add more as needed
};

/**
 * Legacy fallback prettifier (kept from original).
 */
export const idToName = (id: string): string => {
    const nameWithoutRoman = startCase(toLower(id.replace(/^ENCHANTMENT_/, '')));
    if (!ENDS_WITH_NUMBER.test(nameWithoutRoman)) return nameWithoutRoman;

    const [n, ...strings] = nameWithoutRoman.split(' ').reverse() as [string, ...string[]];
    const decimal = Number.parseInt(n, 10);
    const romanNumeral = decimal <= 0 ? decimal : romans.romanize(decimal);
    return [romanNumeral, ...strings].reverse().join(' ');
};

export const formatItemName = ({
                                   name,
                                   skyblockItemId,
                               }: {
    name: string | null;
    skyblockItemId: string;
}): string => {
    if (NAME_OVERRIDES[skyblockItemId]) return NAME_OVERRIDES[skyblockItemId];

    if (name) {
        const cleaned = name
            .replace(COLOR_CODE_PATTERN, '')
            .replace(PLACEHOLDER_PATTERN, '')
            .trim();

        // Keep your STARRED_ handling (fragged items)
        if (skyblockItemId.startsWith('STARRED_')) {
            return `⚚ ${cleaned}`;
        }
        return cleaned;
    }
    // Fallback
    return idToName(skyblockItemId);
};

function assertSuccess<T extends { success: boolean }>(resp: any, guard: (r: any) => r is T, label: string): T {
    if (!guard(resp)) {
        const cause = (resp as ApiErrorResponse)?.cause ?? 'Unknown API error';
        throw new Error(`${label} API returned an error: ${cause}`);
    }
    return resp;
}

function isItemsSuccess(r: any): r is SkyBlockItemsApiResponse {
    return r && r.success === true && Array.isArray(r.items);
}
function isBazaarSuccess(r: any): r is BazaarApiResponse {
    return r && r.success === true && typeof r.products === 'object' && r.products !== null;
}

async function fetchJson<T>(url: string): Promise<T> {
    const res = await fetch(url, { headers: { 'User-Agent': 'bazaar-utils-generator' } });
    if (!res.ok) throw new Error(`Request failed ${res.status} ${res.statusText} for ${url}`);
    return res.json() as Promise<T>;
}

async function generateBazaarConversions() {
    console.log('Fetching Bazaar products and SkyBlock items...');
    const [bazaarRaw, itemsRaw] = await Promise.all([
        fetchJson<any>(BAZAAR_API_URL),
        fetchJson<any>(ITEMS_API_URL),
    ]);

    const bazaarData = assertSuccess(bazaarRaw, isBazaarSuccess, 'Bazaar');
    const itemsData = assertSuccess(itemsRaw, isItemsSuccess, 'Items');

    // Authoritative set of product IDs
    const bazaarProductIds = Object.keys(bazaarData.products);
    console.log(`Bazaar currently lists ${bazaarProductIds.length} product IDs.`);

    // Build quick lookup of items by ID
    const itemsById: Record<string, SkyBlockItem> = {};
    for (const it of itemsData.items) {
        itemsById[it.id] = it;
    }

    const conversions: Record<string, string> = {};
    const missing: string[] = [];

    for (const productId of bazaarProductIds) {
        const item = itemsById[productId];
        if (item) {
            conversions[productId] = formatItemName({ name: item.name, skyblockItemId: productId });
        } else {
            // Not found in items API; fallback
            conversions[productId] = formatItemName({ name: null, skyblockItemId: productId });
            missing.push(productId);
        }
    }

    // Sort keys for deterministic output
    const sorted: Record<string, string> = {};
    Object.keys(conversions)
        .sort((a, b) => a.localeCompare(b))
        .forEach((k) => (sorted[k] = conversions[k]));

    fs.writeFileSync(OUTPUT_PATH, JSON.stringify(sorted, null, 2));
    console.log(`Wrote ${Object.keys(sorted).length} bazaar conversions to ${OUTPUT_PATH}`);

    if (missing.length) {
        console.log(
            `NOTE: ${missing.length} product IDs not present in items API. Used fallback prettifier.\n` +
            missing.join(', ')
        );
    }
}

generateBazaarConversions().catch((e) => {
    console.error('Failed to generate bazaar conversions:', e);
    process.exit(1);
});