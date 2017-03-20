<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at
      http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->
# Apache NiFi NAR Maven Plugin

Apache NiFi NAR Maven Plugin helps to build NiFi Archive bundles to support the classloader isolation model of NiFi.

## Table of Contents

- [Requirements](#requirements)
- [Getting Started](#getting-started)
- [Getting Help](#getting-help)
- [License](#license)

## Requirements
* JDK 1.7 or higher
* Apache Maven 3.1.0 or higher

## Getting Started

Building the nifi-nar-maven-plugin module should be rare since it will be released infrequently compared to
the main 'nifi' code tree.

- Build with `mvn clean install`
- Presuming you need to make use of changes to the nifi-nar-maven-plugin module, you should next
  go to the [nifi](../nifi) directory and follow its instructions. 


## Settings and configuration

There are several properties that can be set to change the behavior of this plugin.
Two of special interest are:

####type
type defines the type of archive to be produced and evaluated for special dependencies.  The default type is nar.  This property should be changed if you have need to 
customize the file extension of archives produced by this plugin.  This plugin will build archives with .nar extentions, and look for othe .nar dependencies by definition.
Changing this value, for example to 'foo', will have the effect of having the plugin produce archives with .foo as the extension, and look for .foo files
as nar dependencies.
 
####packageIDPrefix 
The archives produced by this plugin have the following entries added to the manifest ( where packageIDPrefix defaults to 'Nar'):

-  {packageIDPrefix}-Id
-  {packageIDPrefix}-Group
-  {packageIDPrefix}-Version
-  {packageIDPrefix}-Dependency-Group
-  {packageIDPrefix}-Dependency-Id
-  {packageIDPrefix}-Dependency-Version

This property can be used to change the name of these manifest entries

 

## Getting Help
If you have questions, you can reach out to our mailing list: dev@nifi.apache.org
([archive](http://mail-archives.apache.org/mod_mbox/nifi-dev)).
We're also often available in IRC: #nifi on
[irc.freenode.net](http://webchat.freenode.net/?channels=#nifi).


## License

Except as otherwise noted this software is licensed under the
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

