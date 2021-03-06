RIPE NCC RPKI Validator
=======================

Source Code
-----------

The RIPE NCC RPKI Validator is an open source project on Github. Please contribute!

  https://github.com/RIPE-NCC/rpki-validator/


Support
-------

Please contact <certification@ripe.net> with any questions relating to the
RIPE NCC RPKI Validator or the RIPE NCC Resource Certification (RPKI) service.


Requirements
-------------

= A Unix-like operating system.

= Rsync(1), which must be available by just typing the 'rsync' command. The validator 
  uses the following rsync(1) options: --update, --times, --copy-links, --recursive, and
  --delete.
  
= Oracle JDK 7

  This software was developed and tested using Oracle JDK 7. This Java version should be
  available without restrictions for all major platforms, though it may not be included 
  in your distribution by default.

  You can check which version of Java you have by running:

  $ java -version
  
  The start script will try to find java on the path, using 'which java'.

  If this is not what you want, e.g. because you have multiple java versions on your
  system, or you just don't want to have it on the normal path, then you can specify
  a java installation explicitly by setting the JAVA_HOME directory.

  Beware that JAVA_HOME should not point to the java executable itself, but the 
  installation directory of your java distribution. The executable is expected here:
      
  $JAVA_HOME/bin/java

= At least 1GB of free memory

  For performance this tool keeps a lot of data in memory. This also helps multi-threading
  allowing the tool to work faster by doing tasks in parallel.
  
  
Usage
-----

= Decompress the downloaded package
= Run the RPKI Validator script from the root folder to start, stop and check the status 
  of the application

       ./rpki-validator.sh start  [-c /path/to/my-configuration.conf]
   or  ./rpki-validator.sh stop   [-c /path/to/my-configuration.conf]
   or  ./rpki-validator.sh status [-c /path/to/my-configuration.conf]
   
  Note: you only have to use the -c flag if you change the name and/or location of the
  configuration file, as explained below.   

= Once the application has started, it will write the current PID to rpki-validator.pid 
  and start logging to the log directory. You can access the web user interface here:
  
  http://yourhost:http-port/    (e.g. http://localhost:8080/)

                           
Configuration file
------------------

You can override the default settings of the RPKI Validator by editing the configuration
file at the following location:

  <root-folder>/conf/rpki-validator.conf

If you want to be sure that future upgrades do not overwrite your local changes, you may 
want to make a local copy of the configuration file and refer to it explicitly using
the -c option. For usage, please refer to the configuration file comments.  
  

Notes on kiosk mode
-------------------

In kiosk mode the application will be accessible read-only to anyone, but any action or
update will require authentication with a username and password. This a basic, 
experimental feature. Kiosk mode is merely intended to prevent unauthorised people from 
making (accidental) changes. The password you configure is stored in plain text. When a 
user enters the credentials, they are sent unencrypted. Lastly, the credentials remain 
valid for the entire browser session, so you need to quit your browser to log out.


Configuring additional Java Virtual Machine (JVM) options
---------------------------------------------------------

The configuration file allows you to change the most import memory options and/or specify
a SOCKS or HTTP proxy to be used by the Java Virtual Machine (JVM) that will run this
application.

Additionally, you can use the JAVA_OPTS environment variable to pass in more JVM options,
not supported by the configuration file and start script. Use this very carefully though,
we can give no guarantees about the result.


Configuring Trust Anchors
-------------------------

This validator will automatically pick up any file matching this pattern:  

  <root-folder>/conf/tal/*.tal

The Trust Anchor Locator (TAL) files for four Regional Internet Registries are included
with this distribution: AFRINIC, APNIC, LACNIC and RIPE NCC. 

To access ARIN's TAL, you will have to agree to ARIN's Relying Party Agreement. After 
that, the TAL will be emailed to the recipient. Please visit this ARIN web page for
more information: http://www.arin.net/public/rpki/tal/index.xhtml

After obtaining ARIN's TAL, please copy it to the following location to use it:
 
  <root-folder>/conf/tal/

If you compare the format of the included files to the Trust Anchor format defined here:

  http://tools.ietf.org/html/rfc6490

you will notice that the format used here is slightly different. We are using key-value 
pairs to allow specifying some additional information. Make sure that you enter a value 
for ca.name. The certificate.location and public.key.info correspond to the location and 
subjectPublicKeyInfo fields in the standard. The prefecth.uris field is optional. You may 
specify a comma separated list of rsync URIs for directories here, that will be 
'pre-fetched'. This helps performance for repositories that have a flat structure 
(children not published under parents).

Example:  

  ca.name = ARIN RPKI Root
  certificate.location = rsync://rpki.arin.net/repository/arin-rpki-ta.cer
  public.key.info = MIIBI..... etc 1 LINE
  prefetch.uris = rsync://rpki.arin.net/repository/

 
API
---

This validator has a RESTful API that allows you to get the full validated ROA set.

Usage:

= CSV format:  http://yourhost:http-port/export.csv
= JSON format: http://yourhost:http-port/export.json

You can also query this RPKI Validator for validity information about a BGP announcement. 
You will get a response in JSON format containing the following data:

= The RPKI validity state, as described in RFC 6811
= The validated ROA prefixes that caused the state
= In case of an 'Invalid' state, the reason:
    = The prefix is originated from an unauthorised AS
    = The prefix is more specific than allowed in the Maximum Length of the ROA

Usage:

= http://yourhost:http-port/api/v1/validity/:ASN/:prefix

e.g.

= IPv4: http://yourhost:http-port/api/v1/validity/AS12654/93.175.146.0/24
= IPv6: http://yourhost:http-port/api/v1/validity/AS12654/2001:7fb:ff03::/48

Full documentation can be found here:

  https://www.ripe.net/developers/rpki-validator-api

Deep Links
----------

You can specify an AS Number or prefix in the URL of the ROA and BGP Preview pages to get
direct, bookmark-able access to information. For example:

= http://yourhost:http-port/roas?q=93.175.146.0/24
= http://yourhost:http-port/bgp-preview?q=AS12654


Monitoring
----------
You can monitor the health of the application itself using this url:

   http://yourhost:http-port/health

This url will return data in JSON format with information on each test.

Monitoring tools should check the http status of the response.
    200 -> Everything is OK
    500 -> One or more checks failed

For the moment we only check that the rsync binary can be found and executed, but we may
add more checks in the future.

In addition, each Trust Anchor has a dedicated monitoring page showing statistics, 
validation warnings and errors. Clicking one of the "Processed Items" links on the Trust 
Anchors page will take you to an overview with all checks and warnings for that trust 
anchor.

You can set up your monitoring tool to check for the contents of this page. Using 
regular expressions, check for the label in the span tag with the id "healthcheck-result":

   <span id="healthcheck-result">.*OK.*</span>
   <span id="healthcheck-result">.*ALERT.*</span>


Known Issues
------------

= The validator does not check for revocations or expiration times in between validation 
  runs

= The validator does not support incremental updates as defined here, yet:
  http://tools.ietf.org/html/rfc6810#section-6.2
  
  When the validator has any updates, it will respond with a cache-reset, as described 
  here: http://tools.ietf.org/html/rfc6810#section-6.3


Version History
---------------

2.16 - 21 March 2014
= Fixed memory leak

2.15 - 3 January 2014
= More refinements to the trust anchor monitoring functionality to ensure alerts are 
  triggered accurately and lower the chance of a false positive
= Added an alert if more than 10% of objects have a validation error
= Carrying over the alert for an unexplained drop in object count over subsequent 
  validation runs until the object count is restored and/or no more errors are observed
= No longer warns when manifest EE certificate validity times do not match the manifest 
  "this update" and "next update" times
= Warn about stale manifests and CRLs for up to X days, as configured by the user 
  (default = 0), reject after
= Reject manifests with expired EE certificates
= A more detailed error message is displayed when a ROA is rejected because it refers to
  resources the holder no longer has on their certificate

2.14 - 9 December 2013
= Fixed an issue where the wrong CRL could be used when a remote repository is being 
  updated during validation
= Improved monitoring code to allow for easier tracking of alerts
= Several clarifications in the text

2.13 - 22 November 2013
= The application now uses a single configuration file to override all default settings.
= The application will now try to find your Java installation if you have not specified
  your JAVA_HOME.
= Added basic application and Trust Anchor monitoring
= Bug and conformance fixes, as well as other magical improvements

2.12 - 25 October 2013
= Changed default memory settings from 512MB to 1024MB after out of memory problems with
  the current size of RPKI repositories
= Added experimental support to run validator in "Kiosk" mode

2.11.1 - 12 July 2013
= Bug fix release, validator was rejecting *all* subsequent manifests as soon as one
  object was rejected for a Trust Anchor. 
= All users are recommended to upgrade to this release.

2.11 - 26 June 2013
= Application packaging is now in tar format
= Included a script to start, stop and check the status of the application
= It is now possible specify a proxy server for outgoing HTTP connections, which the
  application uses to retrieve the RIS Route Collector dump files.
= Added a RESTful API that allows users to request the RPKI validity state of a BGP 
  announcement.
= In the BGP Preview tab, Invalid announcements now display the reason for the state.

2.10 - 5 June 2013
= Validated ROA cache can now be exported in JSON format
= Router sessions logging format has changed according to RFC 6810

2.9 - 7 May 2013
= Fix an issue that breaks the RTR interface

2.8.1 - 12 April 2013
= Fix performance issue introduced in 2.8

2.8 - 5 April 2013
= Added a warning if objects with unknown extensions (such as *.gbr) are found.

2.7 - 28 November 2012
= Made validator and projects this depends on available on GitHub and Maven Central
= Updated pre-configured Trust Anchor Files for APNIC and LACNIC
= Made Trust Anchor handling more robust
= Disabled warnings about failure to send performance metrics

2.6 - Internal release

2.5 - 4 September 2012
= Fixed a thread leak bug
= Cleaned up experimental and pilot TAL files. Release now only includes TALs of these 
  four RIRs: AFRINIC, LACNIC, APNIC and RIPE NCC
= Added information to the README.txt how to obtain and use ARIN's TAL

2.4 - 2 July 2012
= Cache repository objects for re-use in case of problems retrieving objects (expired 
  objects are still rejected / warned about as per configuration)

2.3 - 9 May 2012
= Added performance metrics to the RPKI Validator
= Small UI changes on White List page

2.1 - 24 April 2012
= Fixed a bug where in some cases fetching RIS Route Collector data would be slow or 
  failed
= Trust Anchors can now be easily enabled or disabled with a check mark
= Added a dedicated User Preferences page

2.0.4 - 10 April 2012
= Added a "Process Items" section to Trust Anchor page, displaying number of accepted 
  items, warnings and errors
= Added a dedicated Validation results page for inspecting errors and warnings
= Fetching route collector data for the BGP Preview is more robust and indicates the time 
  of the last retrieval

2.0.3 - 16 February 2012
= Fixed a bug that caused certain types of IPv6 notation to break the BGP Preview
= The validator can optionally check for updates of the application

2.0.1 - 3 January 2012
= Performance and stability improvements

2.0 - 13 December 2011
= Initial release of the next generation RPKI Validator toolset:
= It runs as a service and has an intuitive web-based interface
= It allows manual controls and overrides through filters and white lists
= It allows integration in existing (RPSL based) workflows
= It is capable of communicating with RPKI-capable routers
