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
* JDK 1.8 or higher
* Apache Maven 3.1.0 or higher

## Getting Started

Building the nifi-nar-maven-plugin module should be rare since it will be released infrequently compared to
the main 'nifi' code tree.

- Build with `mvn clean install`
- Presuming you need to make use of changes to the nifi-nar-maven-plugin module, you should next
  go to the [nifi](../nifi) directory and follow its instructions. 


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

