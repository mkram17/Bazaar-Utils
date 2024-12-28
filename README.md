# TO DO
Remove item when it is claimed
Make sure that it removes/fills the correct item when order is filled
~~Identify item to copy by order volume and price in order options menu~~
~~Allow pasting into signs (skyhanni has that)~~
Make config
Remove item when order is cancelled
~~Improve item name to productID conversion (use diff json --from neu?-- and move method to itemData class)~~
Dont need to find item to get the new price for it for flip? or at least make a backup using item lore in flip gui if cant find price

Potential bugs:
make two of the exact same order (volume and item), but the one made later is filled first. Might not remove correct item from watchedItems
Seems like it might not find the item in order page all of the time
Util.notifyAll produced null pointer exceptions when in tickEvent

Bugs:
finding item from price and volume does not work if two items have the same volume (and price) -- maybe try to use amount filled?


Thanks:
To nea89o for his amazing modding guides
To meyi from Bazaar Notifier for the resource conversion json