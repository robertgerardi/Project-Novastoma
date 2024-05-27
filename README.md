# 21-Colostomy-bag-monitoring--Novastoma-

Project 21 – Novastoma Documentation for XIAO nRF52840 and Nordic SDK Nick Urch – nurch@ncsu.edu 
1. Set up Nordic SDK and the toolchain in VS Code. 
a. For a step-by-step guide on setting up the Nordic SDK and the toolchain in VS Code,  refer to the tutorial video available at  
https://www.nordicsemi.com/Products/Development-software/nRF-Connect SDK/GetStarted#infotabs 
b. To begin, download the code from GitHub and save it to your device. Ensure that the  folder you save the code to does not contain any spaces. Then, in the nRF extension  tab in VS Code, under the “Welcome” tab, click open an existing application. Next,  navigate to the folder where you saved the code, and click ok to open it in VS Code. 
c. In the “Application” tab, click “Build all configurations.” This will build the  application and prepare it for upload. 
2. Reprogramming the XIAO nRF52840 board. 
a. A small reset button is next to the USB-C connection. Press it twice to put the board  into bootloader mode. 
b. Windows will ask you what you want to do when this device is found. You can click  to open File Explorer automatically. 
c. In Windows, this device will appear as a storage device with its own drive, similar to  a USB storage device. It will be named “XIAO-SENSE” even though this is a non sense board. 
d. Open the folder where you saved the application and navigate to the “build\zephyr” directory. There will be a file named “zephyr.uf2”. Either copy/paste or drag/drop  this file into the XIAO-SENSE. 
e. The bootloader will download the code and reset the board. The new application  will then run. 
3. Files included. 
a. Prj.config – This is the configuration file for the application. This includes all config  settings needed for the MCU’s operation of devices, such as Bluetooth, GPIO, ADC,  and Flash memory. 
b. Main.c – The main program that will run on the MCU. 
c. Battery.h and Battery.c – The header and c files for the battery helper. i. Marcus Alexander Tjomsaas wrote this code, and the GitHub is located at:  https://github.com/Tjoms99/xiao_sense_nrf52840_battery_lib 
ii. You can visit the GitHub above for more information on the battery helper. d. CMakeLists.txt – The make files for the application. 
e. Nova_nRF.overlay – This overlay file maps the GPIO pins on the board. This is not  used only for the Arduino framework. 
4. Code walkthrough – main.c. 
a. Run through of main.c file, leaving out trivial or obvious code. 
b. Lines 1-15: #include files for the Zephyr RTOS and Bluetooth operation. c. Line 18: Register the log module for USB serial debugging and information.
d. Lines 20-33: User-defined data for bag waste level, commands, buffering, and time stamp. 
e. Lines 46-59: Definition for GPIO pins used to power the three sensors and read data. Also, the device pointers for the GPIO port drivers. 
f. Lines 62-64: Preparing the battery helper. 
g. Lines 68-113: Macros to build the GATT Service and Characteristics. These form the  128-bit UUID for the above. The client-characteristic configuration callbacks are  also defined here. These allow a user to subscribe to notification events. Below are  the GATT Characteristic callbacks for reading and writing to the three  characteristics: Data, Battery, and Command. 
h. Lines 116-133: Macro to build the Service and Characteristics with the appropriate  UUIDs and permissions. 
i. Lines 136-145: Setting up the data to send while Bluetooth is advertising. j. Lines 149-267: Bluetooth callbacks for various events. 
i. Connected – When a Bluetooth connection has been established. ii. Disconnected – When a Bluetooth connection has been severed. The  reason for disconnection is logged. The reasons can be found in the  Bluetooth datasheet  
https://www.bluetooth.com/specifications/specs/core-specification-5-3/ iii. Mtu_updated – Information log for when the byte size of the packets has changed. 
iv. On_le_param_updated – When the connection parameters have changed. v. ****_ccc_cfg_changed – When a connection has subscribed/unsubscribed to the characteristic. 
vi. ****_read_characteristic_cb – When a connection reads from the  characteristic. 
vii. ****_write_characteristic_cb – When a connection writes to the  characteristic. 
k. Lines 270-292: Initialize Bluetooth and start advertising. 
l. Lines 295-319: Initialize GPIO. 
m. Lines 322-326: Calling the battery helper handler. 
n. Lines 329-398: User functions. 
i. Get_Bag_Level: This function powers the sensors through the GPIO and then  reads their levels. After the sensors are read, they are powered back down to  save the battery. 
ii. Create_Outgoing_Data: This function builds a string with the timestamp as  characters [0-9] and the waste level as character [10]. 
iii. Send_Buffer: This function will send out the buffered data from oldest to  newest order. 
o. Lines 401-481: The main loop of the application. This will initialize all the  peripherals and ready the Bluetooth. In the for(;;) loop, the program will get the  waste level of the bag, check for Bluetooth and send out the data if connected. If  not connected and a connection is lost, the data is buffered. The thread will then  sleep for a specified amount of time.
5. Zephyr Documentation can be found at: https://www.zephyrproject.org/ 6. More information about the XIAO nRF52840 can be found at:  
https://www.seeedstudio.com/Seeed-XIAO-BLE-nRF52840-p-5201.html 
7. XIAO nRF52840 Pinout.  
a. Note: The bottom pins marked P1.11 (Tx) and P1.12 (Rx) did not seem to work when  trying to power or read from the sensors. These may be used for Serial or UART  
communication, but I did not look into the problem; I just avoided using them. 8. I can be reached at nurch@ncsu.edu for any questions. If, after graduation, that e-mail is  deactivated, you can e-mail me at nmu0901@yahoo.com or message me on Discord at  nicku252.




Project 21- Novastoma - Android Application Documentation
Robert Gerardi - rvgerard@ncsu.edu

Environment

I wrote the application using Java and Android Studio as the IDE. Android studio can be pretty difficult to work with, and usually has problems when downloading from repositories and just implementing it. You will probably need to fix some environment and settings issues, such as API and SDK targets, to get it working on a different device. Some things I used are also deprecated in newer versions of Android Java, so keep that in mind.
Key Settings
Build.gradle.kts (Module :app) is where you can find the key compile settings.
Minimum SDK = 28
Target SDK  = 34
Target API  = 31
Permissions 
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>

Dependencies

dependencies {
implementation("androidx.appcompat:appcompat:1.6.1")
implementation("com.google.android.material:material:1.10.0")
implementation("androidx.constraintlayout:constraintlayout:2.1.4")
implementation("com.jjoe64:graphview:4.1.0")
testImplementation("junit:junit:4.13.2")
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

Code Sections

AndroidManifest.xml

This file contains permissions that are needed for BLE to work, such as location and scan, as well as reading external storage for database transfer. This file also contains the target API and other activity infrastructure information.
Build.gradle.kts (Module :app)
This file contains the SDK versions and dependencies. The dependencies are listed above if you need to modify the SDK, this may be the place to do it, however, I had issues doing this and needed to create it from scratch to switch the SDK. You can also add dependencies here, just like I did for the graphing dependency (that I did not use).

MainActivity.java

Main Activity has almost all of the code that makes the app function (front end stuff, no libraries or backend). This includes BLE connection and communication, UI functions, waste calculations, and database sending functions. I have commented the code that should describe what stuff is doing, but I can give a higher level description here (in order from top to bottom).
  onResume - resumes application
  testHand/testRun - handler and run functions that send UNIX time to MCU on a 5 second delay after connection
  onCreate - creates the UI, initilizes some BLE stuff and DBHandler, sets up averages on screen
  ScanCallback - after scanning area for BLE devices, if name matches, stop scan and initiate connection
  gattCallback - if connected,  start discovering the services, if not change UI
  onServicesDiscovered - when services are found, get the characteristics, update some UI elements, and send UNIX time on delay
  onCharacteristicWrite/Read - not used but we could use them, we just used the “changed” function, which detects changes in characteristics vs read/writes.
  onCharacteristicChanged - this function detects the changing of the BLE characteristics. It checks which characteristics it is, and does the following actions. If data, parse the data, add to database, and update screen. If it's a battery, add battery percentage to the screen.
  Get_Characteristics - gets the characteristics and assigns them
  BLE_Scan - starts BLE scan for nearby devices
  BLE_Connect/Disconnect - connect initiates the BLE connection with the gatt, disconnect updates disconnection states and gatt
  BLE_Click - based off of the button press, starts scan for MCU or disconnects
  Update_Progress - updates waste bar progress and test UI
  Update_Batt - updates battery percentage on screen
  Update_Button - updates button UI and states
  byteArrayToString - was once used, but not anymore, left it in in case you use it in the future, but it transfer byteArrays to strings
  saveData - initiates data transfer to server
  displayAverages - displays the averages on screen, does calculations from database
  FileSend - this is a Class that has the file transfer code inside, pulling the data from local cache on phone, and sending to server
  dataPoint - this is a Class that defines the datapoint and values inside
  
DBHandler.java

This is the other java file within the project. It controls the SQLite Database. I will highlight the functions below (from top to bottom)
onCreate - creates the SQLite query 
  addNewDataPoint - adds new datapoint into the database
  readDataPoints1Day/3Day/Week - these functions read the datapoints from the database stored locally and compile the data into an array list.
  onUpgrade - check if a table already exists and avoids creating a new one
  
Activity_main.xml

This is an important file and controls the UI element characteristics and settings. It controls their shape, color, names, and positions. If you are changing the UI, you are touching this file. If you go in the file, you can see the different elements, where they are placed, and where they are constrained.
Colors/strings/themes.xml
These files are other xml files that contain some strings, colors, and themes that are used for the UI.

Other Files
  Server.java - file not included in Android. It is the file I used to setup the server, shows IP and when a file is received. 

There are various other files around, but most I did not touch. If you have questions about a file I did not mention, let me know.

Activating Developer Mode on Android Device

The target device must first be put into Developer Mode to debug and test the application.
This process is straightforward and outlined in the link below:
https://developer.android.com/studio/debug/dev-options 

Offloading

Steps I took to offload the application to the phone.
Plug in phone
Make sure you allow the device to connect and give it proper permissions, might have to go to computer settings
Click play in top bar
Wait for it compile and offload to phone
If you run without plugging in the phone, it will run a virtual phone, which is borderline useless to check anything but UI.

You can keep the phone plugged in and check the logcat down in the bottom left to expose exceptions, errors, and actual outputs/behavior from the application.

ALSO, make sure you are allowing permissions on the actual phone itself, as the application will need certain permissions to operate.


Issues

Below are some issues that are likely to arise and some possible solutions.

Starting Again
Using my code and starting again might cause some problems. Android was funky about this when I tried this at the beginning, working off of Nick's old code. Sometimes versions are off so I throw a tantrum. Might be a good idea to start from a point where you can copy and paste most of my code into a new project, and start from there. You can also use my code as a guide and start from scratch, which might be better for newer phones. If you try to use an IPhone, it will be similar to the android code, but iOS operates differently. So, be careful with this.

The Graphing Library
In the dependencies section, there is a link to the jjoe64 graphing library.
“com.jjoe64:graphview:4.1.0"
This library has the ability to be a useful way of graphing the data points on the screen for the user. However, it is difficult to use, and the documentation is lacking. I would suggest experimenting with this further, or see if there is a different library out there that can do similar things.

Alternate Phones
I have tried to test this on Nick's new Android phone, but experienced an immediate crash due to problems with the Bluetooth adapter. I suspect it's because his watch was already connected via bluetooth, but I am unsure about this. I was unable to run further tests.

The colors on the application were also different on his phone, which was weird but I think different phones are going to interpret colors differently. This might need some further investigation.

Support
I will post some pages that will support future development. I will be doing my masters, so if you need support from me, you can reach me at rvgerard@ncsu.edu

BLE Info: Bluetooth Low Energy  |  Connectivity  |  Android Developers
SQLite Info: Save data using SQLite  |  Android Developers
Socket Server: Socket Programming in Java - GeeksforGeeks
Layouts: Develop a UI with Views  |  Android Studio  |  Android Developers


