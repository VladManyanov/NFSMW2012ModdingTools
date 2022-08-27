# NFSMW2012ModdingTools
Check out the example of mod, done with EventCarsListEdit: https://nfsmods.xyz/mod/3971

This tool allows you to change the EasyDrive event list for various cars in Need for Speed: Most Wanted (2012) game, on single-player mode.
Result file will be unpacked and repacked with 1:1 byte data if compare with original file (F7_30_0F_00.dat).
Before you start making new events, you must get the file itself, and learn about unpacked file contents.

## Where I should get the data file?
To get F7_30_0F_00.dat file, you should unpack GAMEPLAY.BNDL file in /GAMELOGIC/ folder, by using Noesis tool with NFS MW 12 Unpacker script.

Script link: https://drive.google.com/drive/folders/1EvcpCqHMHY7BluTEHg6PhzsQCq9dUuZ2
(*Google for tool usage information!*)

After that, proceed to unpack/15_00_00_00 and copy F7_30_0F_00.dat file into the folder with EventCarsListEdit. Launch Unpack.bat file.

## How to work with JSON version?
Please proceed to this page: https://github.com/VladManyanov/NFSMW2012ModdingTools/wiki/F7_30_0F_00-file-contents

## How to use it in-game?
Launch Repack.bat file, and you will get a fresh F7_30_0F_00_new.dat file (*if you did the JSON syntax correctly*). Last thing you need to do - is copy .dat file back in unpack/15_00_00_00 folder with replace (*name it as original*), and repack the BNDL file with Noesis repacker plugin. 
Replace the result BNDL file. If you did everything correctly, game should run fine.

## How to compile?
Only additional library you need is GSON library, used for JSON text stuff.

https://search.maven.org/remotecontent?filepath=com/google/code/gson/gson/2.9.1/gson-2.9.1.jar

## Thanks to...
NIVSAYZ for initial offset/data findings in Event Cars List file.
