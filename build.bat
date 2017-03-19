@ECHO off
SET DOCS="doc"
@RD /S /Q %DOCS%
javadoc -d %DOCS% src\ru\ifmo\ctddev\Zemtsov\implementor\Implementor.java -link https://docs.oracle.com/javase/8/docs/api -private -classpath java-advanced-2017\artifacts\JarImplementorTest.jar;java-advanced-2017\lib\junit-4.11.jar;java-advanced-2017\lib\hamcrest-core-1.3.jar;java-advanced-2017\lib\jsoup-1.8.1.jar;java-advanced-2017\lib\quickcheck-0.6.jar