Requirements:
1. webgraph-3.4.3
2. dsiutils-2.2.3
3. fastutil-6.6.3
4. jsap-2.1
5. sux4j-3.2.2
6. trove-3.1a1-src
7. javatuples-1.2


There are two implementations in this repository


# SWeG (Lossless/Lossy Graph Summarization)

SWeG is one of the state-of-the-art algorithms based on correction-set framework. You can download the paper from [here](https://dl.acm.org/doi/10.1145/3308558.3313402)
## How to run it? 
1. Navigate to the directory Java files exist.
2. Compile all files in the following way: (the required jar files are in the lib directory)

javac -cp "lib/*" -d bin *.java

3. Run Sweg.class with the following input parameters:

java -cp "lib/*":"bin/" graph_sum.Sweg [input_graph] [number_of_iteration] [print_iteration_offset] [dropping_ratio]





# LDME

The state-of-the-art (lossless) correction-set basedgraph summarization. You can read the paper [here](https://dl.acm.org/doi/pdf/10.1145/3448016.3457331) for more information. 
## How to run it?
1. Navigate to the directory Java files exist.
2. Compile all files in the following way: (the required jar files are in the lib directory)

javac -cp "lib/*" -d bin *.java

3. Run Ldme.class with the following input parameters:

java -cp “lib/*”:”bin/“ graph_sum.Ldme [input_graph] [num_of_iterations] [print_iteration_offsets] [size_of_hash]

# Remarks 
All the input graphs should be in the [webgraph](https://www.ics.uci.edu/~djp3/classes/2008_01_01_INF141/Materials/p595-boldi.pdf) format because of its wonderful performance in compressing graphs. The datasets in this format can be found in: <http://law.di.unimi.it/datasets.php>

## Preparing Data

There are three files in this format: 

*basename.graph* <br>
*basename.properties* <br>
*basename.offsets*


Let us see for an example dataset, *cnr-2000*, in 
http://law.di.unimi.it/webdata/cnr-2000

There you can see the following files available for download.

*cnr-2000.graph* <br>
*cnr-2000.properties* <br>
*cnr-2000-t.graph* <br>
*cnr-2000-t.properties* <br>
*...* <br>
(you can ignore the rest of the files)

The first two files are for the forward (regular) *cnr-2000* graph. The other two are for the transpose (inverse) graph. If you only need the forward graph, just download: 

*cnr-2000.graph* <br>
*cnr-2000.properties*

What's missing is the "offsets" file. This can be easily created by running:

__java -cp "lib/*" it.unimi.dsi.webgraph.BVGraph -o -O -L cnr-2000__


### Symmetrizing graph
In order to obtain undirected graphs, for each edge we add its inverse. This can be achieved by taking the union of the graph with its *transpose*. Here we show how to do this for cnr-2000.

Download from http://law.di.unimi.it/datasets.php:

*cnr-2000.graph* <br>
*cnr-2000.properties* <br>
*cnr-2000-t.graph* <br>
*cnr-2000-t.properties*

(The last two files are for the transpose graph.)

Build the offsets:

__java -cp "lib/*" it.unimi.dsi.webgraph.BVGraph -o -O -L cnr-2000__ <br>
__java -cp "lib/*" it.unimi.dsi.webgraph.BVGraph -o -O -L cnr-2000-t__

Symmetrize by taking union:

__java -cp "lib/*" it.unimi.dsi.webgraph.Transform union cnr-2000 cnr-2000-t cnr-2000-sym__
