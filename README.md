# UML (/SysML/BPMN) Model Search Engine

## Background
Historically, modeling has been used in the field of software engineering to describe and visualize software systems and their architecture. In the last decade, the practice has seen growth in other engineering principles, such as aerospace, automotive, and electrical, in part due to the introduction of SysML (Systems Modeling Language) which is built on top of UML but adapts the language so as to lend itself to complex systems of various disciplines. Models are represented as deeply inter-connected elements that adhere to a metamodel specification. They are typically serialized as XMI or JSON where each element is a tag or object, respectively.

## Problem
The current state of practice for searching such models using OOB tools is to search applicable attributes of an element for the text provided by the user, essentially treating each element as its own “document”. For example, if the user searched for “spacecraft” the elements “Europa Clipper Spacecraft” and “Reference Spacecraft” would be presented with no ranking.

While the lack of ranking itself presents an issue, a larger problem is the absence of context. Consider the scenario where “Europa Clipper Spacecraft” has a Property called “mass” (treated as a separate element). If the model was then searched for “europa clipper spacecraft mass” it would yield no results since no element contains that whole string in any of its applicable attributes. It would only be by creating a fully qualified name that such a search would yield a result. Searching the fully qualified name is an example of where analysis of the related elements would be necessary to present the user with relevant results.

## Proposal
This project aims to leverage the inter-connected nature of model elements to provide more relevant results to search queries made on the model. The target audience of this tool are engineers who describe, analyze, and communicate their systems using models and would use such a tool to navigate their models for those purposes.

In order to achieve this end, existing technologies like Elasticsearch and open source modeling software like [OpenMBEE](http://www.openmbee.org/) can be leveraged. Due to the parallels that can be drawn from the World Wide Web in the way of inter-connected documents, many of the techniques and algorithms developed for the Internet can be adapted for this use case. For example, techniques like link analysis and PageRank can be adapted to rank relevant search results. The effectiveness of this tool will be judged using relevance feedback from engineers and their real-world models, such as the open source [TMT SysML model](https://github.com/Open-MBEE/TMT-SysML-Model). The usefulness will be evaluated using the Cranfield methodology and/or A-B testing.

## Quickstart

### Part 1: Export Model as JSON (OPTIONAL)
#### Alternative
* Pre-exported model in `$REPO/resources/TMT-json.zip`
* Unzip and replace future references to `$CSM/json` with the output directory
#### Dependencies
* Cameo Systems Modeler (or MagicDraw with SysML plugin bundle)
    * https://www.nomagic.com/products/cameo-systems-modeler
    * 18.5 SP3 (no_install)
    * Trial license in email
* Model Development Kit
    * https://bintray.com/openmbee/maven/mdk
    * 3.2.2
* TMT SysML Model
    * https://github.com/Open-MBEE/TMT-SysML-Model
    * db359a7d744480a0025e03a3558e4387e992b743
#### Initialization
* Extract Cameo_Systems_Modeler_185_sp3_no_install.zip to `$CSM`
* Extract TMT-SysML-Model-*.zip to `$TMT`
* Execute `$CSM/bin/csm`
* Switch to Standalone License
* Select license
* Help -> Resource/Plugin Manager -> Import -> mdk-plugin-3.2.2.zip
* Close Cameo Systems Modeler
#### Steps
* Execute `$CSM/bin/csm`
* File -> Open Project... -> `$TMT/TMT.mdzip`
* Tools -> Macros -> Create Macro...
* Macro Language -> Groovy
* Copy paste contents of `$REPO/resources/dumpjson.groovy` -> Run
* JSON files will be created in $CSM/json for each model element

### Part 2: Build Elasticsearch Index (REQUIRED)
#### Dependencies
* JDK 8
    * http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
    * 8u151
* Elasticsearch
    * https://www.elastic.co/downloads/elasticsearch
    * 6.1.1
#### Initialization
* Install JDK 8
* Extract elasticsearch-6.1.0.zip to $ES
* Run $ES/bin/elasticsearch
#### Steps
* In Terminal, execute `$REPO/gradlew shadowJar`
* In Terminal, execute `java -cp "$REPO/build/libs/uml-search-engine-1.0.0-all.jar" com.github.ivangomes.elasticsearch.cli.InitializeEsIndex --dir $CSM/json`

### Part 3: Build ElementRank Index & Searching (REQUIRED)
#### Dependencies
* Dependencies of Part 2
#### Initialization
* Initialization of Part 2
#### Steps
* In Terminal, execute `java -cp "$REPO/build/libs/uml-search-engine-1.0.0-all.jar" com.github.ivangomes.elementrank.cli.QueryIndices --dir $OUTPUT_DIR --query "queryWord" --query "query phrase" ...`
    * Example queries: `-q "APS" -q "phasing" -q "duration analysis" -q "calculate centroid" -q "select filter" -q "take exposure" -q "acquire lock" -q "offset" -q "send offset" -q "telescope offset" -q "pupil registration" -q "send ack" -q "zernike" -q "close loop"`
* `$OUTPUT_DIR/elementrank_index.json` is the index used to cache the links between elements and the ElementRank of each element.
* `$OUTPUT_DIR/hits_*.json` is the hits for the provided search query with calculated Elasticsearch, ElementRank, and combined scores.
* `$OUTPUT_DIR/test_*.groovy` is a macro that can be used with Cameo Systems Modeler to present the search results.

### Part 4: Present A-B Tests (OPTIONAL)
#### Dependencies
* Dependencies of Part 1
#### Initialization
* Initialization of Part 1
#### Steps
* Execute `$CSM/bin/csm`
* File -> Open Project... -> `$TMT/TMT.mdzip`
* Tools -> Macros -> Create Macro...
* Macro Language -> Groovy
* Copy paste contents of `$OUTPUT_DIR/test_*.groovy` -> Run