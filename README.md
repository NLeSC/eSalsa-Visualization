eSalsa-Visualization
====================

Copyright 2013 The Netherlands eScience Center

What is it?
-----------

The eSalsa visualization tool is an interactive 3D visualization of climatology simulation data generated in the eSalsa project. It is based on the open-source Neon library which is also developed by the Netherlands eScience Center.

Limitations / System requirements
---------------------------------

The software assumes hardware that can support OpenGL 3.0 or greater. It is also written for Java 1.7+. This limits the use of this software 
for both Desktop and mobile devices, except through remote rendering.

Getting started
---------------

Install:
Java SE Development kit 7.0 http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html
Apache Ant: http://ant.apache.org/bindownload.cgi

Once these are installed, typing "ant run" in the root directory (the directory this README is in) will start the eSalsa Visualization.
Use File -> Open to select a NetCDF file, and the program will display that file. 