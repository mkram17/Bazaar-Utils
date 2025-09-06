import * as fs from 'fs';
import * as path from 'path';
import { startCase, toLower } from 'lodash';
import romans from 'romans';

const ITEMS_API_URL = 'https://api.hypixel.net/v2/resources/skyblock/items';
const BAZAAR_API_URL = 'https://api.hypixel.net/v2/skyblock/bazaar';

const OUTPUT_FILE_NAME = 'bazaar-conversions.json';
const OUTPUT_PATH = path.join(process.cwd(), OUTPUT_FILE_NAME);

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
    // Ultimate Enchants that still have "Ultimate" in the name
    ENCHANTMENT_ULTIMATE_WISE_1: "Ultimate Wise I",
    ENCHANTMENT_ULTIMATE_WISE_2: "Ultimate Wise II",
    ENCHANTMENT_ULTIMATE_WISE_3: "Ultimate Wise III",
    ENCHANTMENT_ULTIMATE_WISE_4: "Ultimate Wise IV",
    ENCHANTMENT_ULTIMATE_WISE_5: "Ultimate Wise V",

    ENCHANTMENT_ULTIMATE_JERRY_1: "Ultimate Jerry I",
    ENCHANTMENT_ULTIMATE_JERRY_2: "Ultimate Jerry II",
    ENCHANTMENT_ULTIMATE_JERRY_3: "Ultimate Jerry III",
    ENCHANTMENT_ULTIMATE_JERRY_4: "Ultimate Jerry IV",
    ENCHANTMENT_ULTIMATE_JERRY_5: "Ultimate Jerry V",

    // Ingots
    ENCHANTED_IRON: "Enchanted Iron Ingot",
    ENCHANTED_GOLD: "Enchanted Gold Ingot",

    // Gemstones (Rough)
    ROUGH_AMBER_GEM: "⸕ Rough Amber Gemstone",
    ROUGH_AMETHYST_GEM: "❈ Rough Amethyst Gemstone",
    ROUGH_AQUAMARINE_GEM: "☂ Rough Aquamarine Gemstone",
    ROUGH_CITRINE_GEM: "☘ Rough Citrine Gemstone",
    ROUGH_JADE_GEM: "☘ Rough Jade Gemstone",
    ROUGH_JASPER_GEM: "❁ Rough Jasper Gemstone",
    ROUGH_ONYX_GEM: "☠ Rough Onyx Gemstone",
    ROUGH_OPAL_GEM: "❂ Rough Opal Gemstone",
    ROUGH_PERIDOT_GEM: "\u2618 Rough Peridot Gemstone",
    ROUGH_RUBY_GEM: "❤ Rough Ruby Gemstone",
    ROUGH_SAPPHIRE_GEM: "✎ Rough Sapphire Gemstone",
    ROUGH_TOPAZ_GEM: "✧ Rough Topaz Gemstone",

    // Gemstones (Flawed)
    FLAWED_AMBER_GEM: "⸕ Flawed Amber Gemstone",
    FLAWED_AMETHYST_GEM: "❈ Flawed Amethyst Gemstone",
    FLAWED_AQUAMARINE_GEM: "☂ Flawed Aquamarine Gemstone",
    FLAWED_CITRINE_GEM: "☘ Flawed Citrine Gemstone",
    FLAWED_JADE_GEM: "☘ Flawed Jade Gemstone",
    FLAWED_JASPER_GEM: "❁ Flawed Jasper Gemstone",
    FLAWED_ONYX_GEM: "☠ Flawed Onyx Gemstone",
    FLAWED_OPAL_GEM: "❂ Flawed Opal Gemstone",
    FLAWED_PERIDOT_GEM: "\u2618 Flawed Peridot Gemstone",
    FLAWED_RUBY_GEM: "❤ Flawed Ruby Gemstone",
    FLAWED_SAPPHIRE_GEM: "✎ Flawed Sapphire Gemstone",
    FLAWED_TOPAZ_GEM: "✧ Flawed Topaz Gemstone",

    // Gemstones (Fine)
    FINE_AMBER_GEM: "⸕ Fine Amber Gemstone",
    FINE_AMETHYST_GEM: "❈ Fine Amethyst Gemstone",
    FINE_AQUAMARINE_GEM: "☂ Fine Aquamarine Gemstone",
    FINE_CITRINE_GEM: "☘ Fine Citrine Gemstone",
    FINE_JADE_GEM: "☘ Fine Jade Gemstone",
    FINE_JASPER_GEM: "❁ Fine Jasper Gemstone",
    FINE_ONYX_GEM: "☠ Fine Onyx Gemstone",
    FINE_OPAL_GEM: "❂ Fine Opal Gemstone",
    FINE_PERIDOT_GEM: "\u2618 Fine Peridot Gemstone",
    FINE_RUBY_GEM: "❤ Fine Ruby Gemstone",
    FINE_SAPPHIRE_GEM: "✎ Fine Sapphire Gemstone",
    FINE_TOPAZ_GEM: "✧ Fine Topaz Gemstone",

    // Gemstones (Flawless)
    FLAWLESS_AMBER_GEM: "⸕ Flawless Amber Gemstone",
    FLAWLESS_AMETHYST_GEM: "❈ Flawless Amethyst Gemstone",
    FLAWLESS_AQUAMARINE_GEM: "☂ Flawless Aquamarine Gemstone",
    FLAWLESS_CITRINE_GEM: "☘ Flawless Citrine Gemstone",
    FLAWLESS_JADE_GEM: "☘ Flawless Jade Gemstone",
    FLAWLESS_JASPER_GEM: "❁ Flawless Jasper Gemstone",
    FLAWLESS_ONYX_GEM: "☠ Flawless Onyx Gemstone",
    FLAWLESS_OPAL_GEM: "❂ Flawless Opal Gemstone",
    FLAWLESS_PERIDOT_GEM: "\u2618 Flawless Peridot Gemstone",
    FLAWLESS_RUBY_GEM: "❤ Flawless Ruby Gemstone",
    FLAWLESS_SAPPHIRE_GEM: "✎ Flawless Sapphire Gemstone",
    FLAWLESS_TOPAZ_GEM: "✧ Flawless Topaz Gemstone",

    // Gemstones (Perfect)
    PERFECT_AMBER_GEM: "⸕ Perfect Amber Gemstone",
    PERFECT_AMETHYST_GEM: "❈ Perfect Amethyst Gemstone",
    PERFECT_AQUAMARINE_GEM: "☂ Perfect Aquamarine Gemstone",
    PERFECT_CITRINE_GEM: "☘ Perfect Citrine Gemstone",
    PERFECT_JADE_GEM: "☘ Perfect Jade Gemstone",
    PERFECT_JASPER_GEM: "❁ Perfect Jasper Gemstone",
    PERFECT_ONYX_GEM: "☠ Perfect Onyx Gemstone",
    PERFECT_OPAL_GEM: "❂ Perfect Opal Gemstone",
    PERFECT_PERIDOT_GEM: "\u2618 Perfect Peridot Gemstone",
    PERFECT_RUBY_GEM: "❤ Perfect Ruby Gemstone",
    PERFECT_SAPPHIRE_GEM: "✎ Perfect Sapphire Gemstone",
    PERFECT_TOPAZ_GEM: "✧ Perfect Topaz Gemstone",
};

/**
 * Legacy fallback prettifier (kept from original).
 * Modified to remove "ULTIMATE_" prefix for enchantments not in NAME_OVERRIDES.
 */
export const idToName = (id: string): string => {
    // For enchantments, remove both ENCHANTMENT_ and ULTIMATE_ prefixes
    // unless they have manual overrides (like Ultimate Wise and Ultimate Jerry)
    let cleanId = id.replace(/^ENCHANTMENT_/, '');
    if (cleanId.startsWith('ULTIMATE_') && !NAME_OVERRIDES[id]) {
        cleanId = cleanId.replace(/^ULTIMATE_/, '');
    }

    const nameWithoutRoman = startCase(toLower(cleanId));
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

    const itemName = idToName(skyblockItemId);
    // Handle shard items: convert "Shard [Name]" to "[Name] Shard"
    if (skyblockItemId.startsWith('SHARD_')  && itemName.startsWith('Shard ')) {
        const shardName = itemName.substring(6); // Remove "Shard " prefix
        return `${shardName} Shard`;
    }
    // Fallback
    return itemName;
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