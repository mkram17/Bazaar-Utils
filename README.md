# TO DO
~~Find better way to tell if items have loaded into gui~~
~~Remove item when it is claimed~~
Make sure that it removes/fills the correct item when order is filled
~~Identify item to copy by order volume and price in order options menu~~
~~Allow pasting into signs (skyhanni has that)~~
~~Make config~~
Remove item when order is cancelled
~~Improve item name to productID conversion (use diff json --from neu?-- and move method to itemData class)~~
Dont need to find item to get the new price for it for flip? or at least make a backup using item lore in flip gui if cant find price
~~Update to use onGuiLoaded() instead of onTick when applicable~~
~~Also only let onGuiLoaded() finish when none of items say "loading..."~~
~~Change pricetype to enum~~, make it change when autoflip does its thing
~~Improve developer chat messages~~
~~Switch from using watchedItems item name to the object when possible~~
Detect when orders are outdated
Switch commands to oneconfig?
Update price when item gets flipped
Make method to update orders in "Co-op Bazaar Orders" gui
Find user's bazaar tax
Add buy Max Amount button for buy orders
Make GUIUtils to help with gui events

### Potential bugs:
make two of the exact same order (volume and item), but the one made later is filled first. Might not remove correct item from watchedItems
Seems like it might not find the item in order page all of the time
Using the fewer param findIndex methods might not return correct item
When claiming partial sell orders uses sell price? which wouldnt match price variable because it doesnt update when item is flipped
If order gets filled while not online to get messages, item status is never updated

### Bugs:
finding item from price and volume does not work if two items have the same volume (and price) -- maybe try to use amount filled?
~~finditemindex doesnt work right -- just gets item w same volume~~
Autoflipper adds price to every sign -- very annoying
Claiming order sometimes has diff messages(?) and removing watchedItem doesnt always work
Sometimes findItemPrice gives error

### Thanks:
To nea89o for his amazing modding guides
To meyi from Bazaar Notifier for the resource conversion json
