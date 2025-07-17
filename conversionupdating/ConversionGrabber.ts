import * as fs from 'fs';
import * as path from 'path';

const API_URL = 'https://api.hypixel.net/v2/resources/skyblock/items';
const OUTPUT_FILE_NAME = 'bazaar-conversions.json';
const OUTPUT_PATH = path.join(process.cwd(), OUTPUT_FILE_NAME);

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

        const data: SkyBlockItemsApiResponse | ApiErrorResponse = await response.json();

        if (!isSuccessResponse(data)) {
            const cause = (data as ApiErrorResponse).cause || 'Unknown API error';
            throw new Error(`API returned an error: ${cause}`);
        }

        console.log(`Successfully fetched data. Last updated at timestamp: ${data.lastUpdated}.`);
        console.log(`Processing ${data.items.length} items...`);

        const itemMap = data.items.reduce((map, item) => {
            map[item.id] = item.name;
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