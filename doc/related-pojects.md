
### MapReduce
MapReduce (and its implementations like Hadoop) work with certain type of computations: on first step *Mappers* for every element from input collection emmit list of key/value pairs, then pairs corresponded to the same key groupped (by *Shuffle*), on final step *Reducer* merge values of each key using associative reducing function.

The main problem with MapReduce is a lack of composobility:

> Many computations can be expressed as a MapReduce, but many others require a sequence or graph of MapReduces.

### Spark
Spark is powerful tool that support various backends and data sources. All computation are expressed with collection-like opperation on Spark RDD (Resilient Distributed Datasets) that can be created from HDFS, S3, Cassandra, local file system.

The main issues that I see with it:

* cluster centric, Spark has engine that deal with all tasks in a big task. Probably it can leads to scallability problems
* eventual consistency (???)
* isolation of execution, one machine can be used for different tasks

http://static.usenix.org/legacy/events/hotcloud10/tech/full_papers/Zaharia.pdf

### FlumeJava + MillWheel, Google Dataflow
Have good an article
http://pages.cs.wisc.edu/~akella/CS838/F12/838-CloudPapers/FlumeJava.pdf

### SynapseGrid
???


### Log based
Log is a queue of ordered records. Different application can be subsribed to it and process the records in the same order as they have been pushed to log. A good introduction to it:
http://engineering.linkedin.com/distributed-systems/log-what-every-software-engineer-should-know-about-real-time-datas-unifying

#### Samza from LinkedIn

#### AWS Kinesis
http://aws.amazon.com/ru/kinesis/faqs/
very similar to samza

These things seems to be too heavyweight (and for sure less cost effective) for pure computational purposes.  

### Cascading 
based on Storm now

### Real-time processing
some into .....

#### Summingbird 
based on Cascading

#### Storm
tuples no types
