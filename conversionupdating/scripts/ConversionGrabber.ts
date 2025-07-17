import * as fs from 'fs';
import * as path from 'path';
import { startCase, toLower } from 'lodash';
import romans from 'romans';

const API_URL = 'https://api.hypixel.net/v2/resources/skyblock/items';
const OUTPUT_FILE_NAME = 'bazaar-conversions.json';
// Go up one directory from the script's location to the project root
const OUTPUT_PATH = path.join(process.cwd(), '..', OUTPUT_FILE_NAME);

interface SkyBlockItem {
    id: string;
    name: string;
    [key: string]: any;
}

interface SkyBlockItemsApiResponse {
    success: true;
    lastUpdated: number;
    items: SkyBlockItem[];
}

interface ApiErrorResponse {
    success: false;
    cause: string;
}


//thanks @fw4853 from skyblock.finance!
const ENDS_WITH_NUMBER = /\d$/;

/**
 * Converts a Skyblock Item ID to a Human-Readable Name
 *
 * @deprecated prefer formatItemName
 */
export const idToName = (id: string): string => {
    const nameWithoutRoman = startCase(toLower(id.replace(/^ENCHANTMENT_/, '')));

    if (!ENDS_WITH_NUMBER.test(nameWithoutRoman)) return nameWithoutRoman;

    const [n, ...strings] = nameWithoutRoman.split(' ').reverse() as [
        string,
        ...string[],
    ];

    const decimal = Number.parseInt(n, 10);

    const romanNumeral = decimal <= 0 ? decimal : romans.romanize(decimal);

    return [romanNumeral, ...strings].reverse().join(' ');
};

export const formatItemName = ({name, skyblockItemId,}: {
    name: string | null
    skyblockItemId: string
}): string => {
    if (name) {
        const cleanedName = name
            .replace(/§[0-9a-zA-Z]/g, '')
            .replace(/%%\w+%%/g, '');

        const possiblyFraggedCleanedName = skyblockItemId.startsWith('STARRED_')
            ? `⚚ ${cleanedName}`
            : cleanedName;

        return possiblyFraggedCleanedName;
    }

    // eslint-disable-next-line @typescript-eslint/no-deprecated
    return idToName(skyblockItemId);
};


/**
 * A type guard to check if the response is successful.
 */
function isSuccessResponse(response: any): response is SkyBlockItemsApiResponse {
    return response && response.success === true && Array.isArray(response.items);
}


/**
 * Fetches the SkyBlock items, processes them into an ID-to-name map,
 * and saves the result to a JSON file.
 */
async function generateItemMap() {
    console.log(`Fetching SkyBlock item data from ${API_URL}...`);

    try {
        const response = await fetch(API_URL);
        if (!response.ok) {
            throw new Error(`Network request failed with status ${response.status}: ${response.statusText}`);
        }

        const data = await response.json();

        if (!isSuccessResponse(data)) {
            const cause = (data as ApiErrorResponse).cause || 'Unknown API error';
            throw new Error(`API returned an error: ${cause}`);
        }

        console.log(`Successfully fetched data. Last updated at timestamp: ${data.lastUpdated}.`);
        console.log(`Processing ${data.items.length} items...`);

        const itemMap = data.items.reduce((map, item) => {
            map[item.id] = formatItemName({ name: item.name, skyblockItemId: item.id });
            return map;
        }, {} as Record<string, string>);

        const fileContents = JSON.stringify(itemMap, null, 2);
        fs.writeFileSync(OUTPUT_PATH, fileContents);

        console.log(`Successfully created item map at ${OUTPUT_PATH}`);

    } catch (error) {
        console.error('An error occurred while generating the item map:');
        console.error(error);
        process.exit(1);
    }
}

generateItemMap();