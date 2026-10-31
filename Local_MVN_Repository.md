# Setup Local MVN Repository for a Project

## Setup local MVN repository for command line

### In the root of the project
  - Create a new folder named `.mvn`
  - Create a new file named ` maven.config ` in folder ` .mvn `
  - Add one line like below to the new file ` maven.config `
    - ` -Dmaven.repo.local=C:/Users/yul/.m2/spring/repository `


## Set up IDE (IntelliJ) for the new local repository

### Create a new maven Settings.xml file in ` .m2 ` folder
  - Add a new line into the new Settings.xml file
  - File name: settings_spring.xml
    - ` <localRepository>C:/Users/yul/.m2/spring/repository</localRepository> `

### Open ` Settings ` Dialog Window
  - Go to ` Build, Execution, Deployment ` 
    - Go to ` Build Tools `
      - Go to ` Maven `
        - Check the ` Override ` checkbox for ` User settings file: `
        - Enter the path of the new settings.xml
          - ` C:\Users\yul\.m2\settings_spring.xml `
        - Check the ` Override ` checkbox for ` Local repository: `
        - Enter the path of the new local repository
          - ` C:\Users\yul\.m2\spring\repository `
  - Click ` Apply ` button and ` Ok ` button

