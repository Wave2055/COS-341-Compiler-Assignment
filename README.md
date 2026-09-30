# COS-341-Compiler-Assignment
## Group 13

## Team Members
- Ayush Beekum u23596351
- Jaitin Moodally u23621372
- Kgaugelo Matsena u23658462
- Lusanda Mtembu u23602016

## Compiling the Jar

```
javac --release 17 -d build *.java && jar --create --file COCO-Group13.jar --main-class Driver -C build .
```

The command compiles our java files into the necessary class files and then bundles them into a jar

This jar can be executed on Java version 17 or higher

## Executing the Jar file

You execute the jar by using the command

```
java -jar COCO-Group13.jar 
```

This causes the jar to search for an SPL.txt in its current directory.
If the SPL.txt is not found it will throw an error

Should you wish to use the jar with a specific file you would run 

```
java -jar COCO-Group13.jar <path to file>
```

This overrides the search for SPL.txt and allows you to run the source code from any file on the system should file path be provided


