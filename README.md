Given an input file of newline separated urls, this will output a list of domains linked to by the given urls. Inspired by [Xenu's Link Sleuth](http://home.snafu.de/tilman/xenulink.html).

This is the first non-trivial concurrent program I've written, it uses a simple pub/sub model with a queue of url's being processed by a number of worker threads.  It's really meant to handle gigantic inputs.  The input file is slurped into the queue, and the list of processed urls and linked domains stays in memory the entire time it runs.  A lot of the code is heavily influenced by dakrone's [Itsy](https://github.com/dakrone/itsy), a more general web crawler.

The number of threads used for crawling the given links is configured in the config map at the top of the source.  So is delay for the snapshot thread, which runs every x milliseconds and creates two files, `linked-domains` and `urls-processed`, which contain all the domains linked to by the urls processed so far, and the actual urls that have been processed so far.

####Example
If sample.com links to google.com and yahoo.com, and another domain, example.com, links to google.com and mit.edu, given an input file containing

````
http://sample.com
http://example.com
````

Then running `lein run input-file` will produce a filed called `linked-domains` containing
````
google.com
yahoo.com
mit.edu
````

and  `urls-processed` containing
````
sample.com
example.com
````

Note:  The lines in the output files are in no particular order.
