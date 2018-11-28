Server for city project

How to open this project:  

  0. JDK version >= 8. 8 is prefered. You can use higher version, but some core libs lack for hibernate 5, you need add them manually
  1. install intellij idea 2018
  2. File--Open, choose 'city' directory then OK
  3. make directory  
    server_bin  
        |  
        ------as  
        |  
        ------gs  
    to hold jar, NOT make it into git contorl directory 'server'
    assume your server_bin path is D:\server_bin
  4. edit configuration in idea ide, add a config for account server:  
    0. choose main class Account.AccountServer  
    1. VM options: -Dlog4j.configuration=file:\D:\server_bin\as\log4j.properties  
    2. Program arguments: "D:\server_bin\as\config.ini"
    3. use classpath of module: as_main  
  5. repeat step 4 for game server, change 'as' to 'gs', add "D:\server_bin\gs\hibernate.cfg.xml" to 4.2 seperate with a blankspace
  
  Now you can debug or run in idea ide
  
  
How to package jar:  
input "gradlew build" in Terminal window in idea ide, then you will get jar file in 'server_bin' directory
 
