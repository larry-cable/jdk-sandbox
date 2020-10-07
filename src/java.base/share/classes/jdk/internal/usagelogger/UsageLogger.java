/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.usagelogger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.StandardProtocolFamily;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnixDomainSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
 
import jdk.internal.event.UsageLogEvent; 

/**
 * The class <code>UsageLogger</code> implements the UsageLogger
 * feature, for logging JVM invocations.
 * <p>
 * A call from runtime into postVMInitHook() will invoke
 * UsageLogger. To configure the UsageLogger place a
 * usagelogger.properties file in any of the pre-defined locations:
 * <ul>
 * <li>The path specified by the property jdk.usagelogger.config.file</li>
 * <li>The central file system location
 * </li>
 * <li>${java.home}/conf/management/</li>
 * </ul>
 * <ul>
 * <li>Windows: %ProgramFiles%\Java\conf\management</li>
 * <li>Linux: /etc/java/conf/management</li>
 * <li>MacOSX: /Library/Application Support/Java/conf/management</li>
 * </ul>
 * The above locations will be searched in the order above. First found file
 * will be used.
 * <p>
 * 
 * Errors are only visible on the JVM's standard output if the verbose option is
 * enabled, i.e. users are not troubled by the failure to log. It is possible
 * that certain errors could be sent to the log in future, e.g. preceded by a
 * hash character to mark them as comments.
 * <p>
 * 
 * TODO: fix list...
 * 
 * The format of the record:
 * <ul>
 * <li>jvm start time
 * <li>hostname
 * <li>ip address
 * <li>Java command line arguments
 * <li>java.home
 * <li>java.version
 * <li>jvm.version
 * <li>java.vendor
 * <li>jvm.vendor
 * <li>os.name
 * <li>os.version
 * <li>os.arch
 * <li>vm args
 * <li>java.class.path
 * <li>jdk.modulepath
 * <li>jdk.main.module
 * <li>jdk.module.main.class
 * <li>user.name
 * <li>user.dir
 * <li>user.home
 * <li>java.io.tmpdir
 * <li>jdk.jfr.repository
 * <li>additionalProperties
 * </ul>
 */
public final class UsageLogger {
	public static final Object        runtime; // RuntimeMXBean... if present, otherwise null...
	
    public static final LocalDateTime startTime;
    
    // access RuntimeMXBean via reflection in case its module is not present..
    
	static {
		//runtime = ManagementFactory.getRuntimeMXBean();
		
		runtime = AccessController.doPrivileged((PrivilegedAction<Object>)() -> {
			try {
				final var clazz = Class.forName("java.lang.management.ManagementFactory");

				return (clazz != null) ? clazz.getMethod("getRuntimeMXBean", (Class<?>[])null).invoke(clazz, (Object[])null) : null;
			} catch (Exception e) {
				printDebug(e.getMessage());
				
				return null;
			}
		});
		
		startTime = AccessController.doPrivileged((PrivilegedAction<LocalDateTime>)() -> {
		    Instant startTime = Instant.now(); // fallback if RuntimeMXBean is not available... 
			
			try {
				if (runtime != null) {
					var clazz = Class.forName("java.lang.management.RuntimeMXBean");

					startTime = Instant.ofEpochMilli((long)clazz.getMethod("getStartTime", (Class<?>[])null).invoke(runtime, (Object[])null));
				}
			} catch (Exception e) {
				printDebug(e.getMessage());
			}
				
			return LocalDateTime.ofInstant(startTime, ZoneOffset.UTC); // report start time as UTC
		});
		
	}
	
    private static final String JDK_UL_DEFAULT_CONFIG_FILENAME   = "usagelogger.properties";

    private static final String JDK_UL_PROPERTY_PREFIX           =  "jdk.usagelogger.";

    private static final String JDK_UL_PROPERTY_RUN_MODE         = JDK_UL_PROPERTY_PREFIX + "run.mode";
    private static final String JDK_UL_PROPERTY_CONFIG_FILE_PATH = JDK_UL_PROPERTY_PREFIX + "config.file";

    private static final String JDK_UL_LOGTOFILE                 = JDK_UL_PROPERTY_PREFIX + "logToFile";
    private static final String JDK_UL_LOGFILEMAXSIZE            = JDK_UL_PROPERTY_PREFIX + "logFileMaxSize";

    private static final String JDK_UL_LOGTOURL                  = JDK_UL_PROPERTY_PREFIX + "logToURL";
    
    private static final String JDK_UL_LOGTOUDS                  = JDK_UL_PROPERTY_PREFIX + "logToUDS"; //UDS
     
    private static final String JDK_UL_VERBOSE                   = JDK_UL_PROPERTY_PREFIX + "verbose";
    private static final String JDK_UL_DEBUG                     = JDK_UL_PROPERTY_PREFIX + "debug";
    
    private static final String JDK_UL_ADDITIONALPROPERTIES      = JDK_UL_PROPERTY_PREFIX + "additionalProperties";
    
    private static final String JDK_UL_SEPARATOR                 = JDK_UL_PROPERTY_PREFIX + "separator";
    private static final String JDK_UL_QUOTE                     = JDK_UL_PROPERTY_PREFIX + "quote";
    private static final String JDK_UL_QUOTE_INNER               = JDK_UL_PROPERTY_PREFIX + "innerQuote";

    private static final String DEFAULT_SEP                      = ",";
    private static final String DEFAULT_QUOTE                    = "\"";
    private static final String DEFAULT_QUOTE_INNER              = "'";

    // the following are variable substitution variables that can be included in the log pathname and/or URL...
    
    private static final String JVM_PID_VAR                      = "${jvm.pid}";

    private static final String USER_HOME_VAR                    = "${user.home}";
    private static final String USER_NAME_VAR                    = "${user.name}";

    //private static final String USER_DIR_VAR                   = "${user.dir}";

    private static final String DATE_VAR                        = "${date}";
    private static final String TIME_VAR			= "${time}";
    
    private static final String JVM_UUID_VAR                    = "${jvm.uuid}";
    //private static final String JAVA_IO_TMPDIR_VAR            = "${java.io.tmpdir}";
    
    private static final String HOSTNAME_VAR                    = "${hostname}";
    private static final String IP_ADDRESS_VAR                  = "${ip.address}";
    
    private static final String OS_NAME_VAR                     = "${os.name}";
    
    private static final String JAVA_VERSION_VAR                = "${java.version}";
    private static final String JAVA_VENDOR_VAR                 = "${java.vendor}";
    
    private static final String VARIABLE_PATTERN                = "(\\$\\{[a-z\\.]+\\})"; // compile this as needed... later
    
    private static final DateTimeFormatter TIME_FORMATTER	    = DateTimeFormatter.ofPattern("HH.mm.SS"); //can't use ':' as separator in paths (windows)
        
    // I hate globals like these...
    
    private static final String           separator;
    private static final String           quote;
    private static final String           innerQuote;

    private static final File             usageLoggerPropertiesFile; // null if none found; usage logging is not enabled.
    
    private static final Properties       usageLoggerProperties; // load these later....
    
    private static final boolean          verbose;
    private static final boolean          debug;

    private static final long             logFileMaxSize;    
    
    static { 
    	usageLoggerPropertiesFile = getConfigFilePrivileged();
    	
        usageLoggerProperties = AccessController.doPrivileged((PrivilegedAction<Properties>)() -> {
        	final var props = new Properties();
        	
        	if (usageLoggerPropertiesFile != null) try 
        	    (FileInputStream     fis = new FileInputStream(usageLoggerPropertiesFile);
        		 BufferedInputStream bin = new BufferedInputStream(fis)) {
        		props.load(bin); // load the properties
        	} catch (Exception e) {
        		// e.g. IllegalArgumentException from invalid properties
        		// file.
        		// We have not initialized our properties yet, we don't know
        		// if verbose or debug are set.
        		props.clear(); // at this point we can do nothing -w e do not have a config
        	}

    		return props;
        });

        verbose         = getPropertyValueBoolean(usageLoggerProperties, JDK_UL_VERBOSE, false);
        debug           = getPropertyValueBoolean(usageLoggerProperties, JDK_UL_DEBUG, false);

        separator       = usageLoggerProperties.getProperty(JDK_UL_SEPARATOR, DEFAULT_SEP).trim();
        quote           = usageLoggerProperties.getProperty(JDK_UL_QUOTE, DEFAULT_QUOTE).trim();
        innerQuote      = usageLoggerProperties.getProperty(JDK_UL_QUOTE_INNER, DEFAULT_QUOTE_INNER).trim();

        logFileMaxSize  = getPropertyValueLong(usageLoggerProperties, JDK_UL_LOGFILEMAXSIZE);
    }
    
    /*
     * this enum enumerates and encapsulates all of the usage logger property values that are to be logged on behalf of the runtime
     * the intent is that they are iterated over "in order" ... or accessed individually to extract their associated value(s)
     * 
     * creates a "nice" API abstraction for property handling...
     *
     */
    
    private static enum SystemProperties {
        
        JVM_START_TIME   ((sp) -> new String[] { startTime.toString() }),
        
        HOSTNAME		 (() -> { var host = "localhost"; try { host = InetAddress.getLocalHost().getCanonicalHostName(); } catch (UnknownHostException uhe) {} return new String[] { host }; }),

        IP_ADDRESS		 (() -> { var ip = "0.0.0.0";   try { ip   = InetAddress.getLocalHost().getHostAddress(); }         catch (UnknownHostException uhe) {} return new String[] { ip }; }),
        
        JVM_PID      	 ((sp) -> new String[] { Long.toString(ProcessHandle.current().pid()) } ), //runtime.getPid()
        
        JVM_UUID         ((sp) -> new String[] { getJvmUuid().toString() } ),
        
        USER_NAME,
        USER_DIR,
        USER_HOME,

        JAVA_ARGUMENTS   ((sp) -> new String[] { getPropertyPrivileged("sun.java.command") } ), // injected in JRE from libjli apparently!
        
        JVM_ARGS          ((sp) -> getInputArguments()),
        
        JAVA_CLASS_PATH,
        JAVA_LIBRARY_PATH,
  
        JDK_MODULE_PATH,
        JDK_MODULE_UPGRADE_PATH,
        JDK_MODULE_MAIN,
        JDK_MODULE_MAIN_CLASS,
        
        JAVA_HOME,
        
        JAVA_VERSION,
        
        JAVA_RUNTIME_VERSION,
        JAVA_RUNTIME_NAME,
        
        JAVA_SPECIFICATION_VERSION,
        JAVA_SPECIFICATION_VENDOR,
        JAVA_SPECIFICATION_NAME,
        
        JAVA_VENDOR,
        JAVA_VENDOR_VERSION,
        JAVA_VERSION_DATE,
        
        JAVA_VM_SPECIFICATION_VERSION,
        JAVA_VM_SEPCIFICATION_VENDOR,
        JAVA_VM_SPECIFICATION_NAME,
        
        JAVA_VM_NAME,
        JAVA_VM_VERSION,
        JAVA_VM_VENDOR,
        
        JAVA_CLASS_VERSION,
        
        JAVA_COMPILER,
        
        OS_NAME,
        OS_VERSION,
        OS_ARCH,
        
        //JAVA_IO_TMPDIR,
        
        //JDK_JFR_REPOSITORY,
        
        //JDK_USAGELOGGER_CONFIG_FILE ((e) -> new String[] { usageLoggerPropertiesFile.getAbsolutePath() } ),
        
        ADDITIONAL_PROPERTIES       ((e) -> getAdditionalProperties(usageLoggerProperties)); // NOTE: this depends upon the usagelogger.properties being loaded...

        private static final Pattern JSONNumber = Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");
        
        private SystemProperties(Function<SystemProperties, String[]> ftn) {
            this.values  = ftn;  // values is the 'getter' for the value(s) of the property...
        }
        
        private SystemProperties(PrivilegedAction<String[]> action) {
        	this.values = (sp) -> { return AccessController.doPrivileged(action); };
        }
        
        private SystemProperties() {
            this((sp) -> new String[] { getPropertyPrivileged(sp.propertyName()) }); //default is to fetch the matching System property...
        }
        
        private String propertyName() {
            return name().toLowerCase().replace('_', '.'); // morphs enum 'name' to corresponding property string name... used to access System props by name
        }
        
        public Map.Entry<String, String[]> getPropertyAndValues() { 
            return new AbstractMap.SimpleImmutableEntry<String, String[]>(propertyName(), values.apply(this));
        }
        
        private String[] getValues() { // note: string[] return
            return getPropertyAndValues().getValue();
        }
        
        private String  getValue() { final var values = getValues(); return values == null ? null : values[0]; } // NOTE: returns [0] element *only* or null 
        
        /*
         * this will return the property as a JSON formatted string e.g:
         * "key" : "value" 
         * "key" : [ "value" , "value" ]
         * "key" : null
         * "key" : true
         * "key" : false
         * "key" : <JSON number>
         * 
         * note it is not recursive, so no embedded object support... (needed)
         */
        private StringBuilder formatPropertyAndValuesAsJSON() {
            final var entry  = getPropertyAndValues();
            final var key    = entry.getKey();
            final var values = entry.getValue();
            final var sb     = new StringBuilder();
            
            if (this == SystemProperties.ADDITIONAL_PROPERTIES) {
                // additional props requires special formatting since the actual value of the property is a list of additional property names ...
                // so we have to perform an additional 'fetch' of those ... 

            var nValues = values != null ? values.length : 0;
                
            if (nValues == 0) // 
                return formatAsJSON(sb, key, values);
                
                sb.append("\"" + key + "\" : [ "); // fmt as an array...
            
                for (String ap : values) { // iterate over additional properties ...
                    sb.append("{ "); // of objects...
                    formatAsJSON(sb, ap, new String[] { getPropertyPrivileged(ap, null) });
                    sb.append(" }");
                    if (--nValues > 0) sb.append(", ");
                }

                sb.append(" ] ");
            } else
                return formatAsJSON(sb, key, values);

            return sb;
        }
        
        private StringBuilder formatAsJSON(final StringBuilder sb, final String key, final String[] values) {
            sb.append("\""+ key +"\"");
            sb.append(" : ");
            
            if (values == null || values.length == 0 || "".equals(values[0]))
                sb.append("null");
            else {
                var length = values.length;

                final var isArray = length > 1;

                if (isArray) sb.append("[ ");

                for (String v : values)  {
                    if (v == null || "".equals(v))
                        sb.append("null");
                    else {
                        if (JSONNumber.matcher(v).matches()) {
                            sb.append(v); // simply emit the number... don't quote
                        } else switch (v) {
                        case "true":  sb.append("true"); break;
                        case "false": sb.append("false"); break;
                        default: 
                            sb.append("\"" + v + "\"");
                        }
                    }

                    if (--length > 0) sb.append(", ");
                }

                if (isArray) sb.append(" ]");
            }
            
            return sb;
        }
        
        StringBuilder FormatPropertyValues(BiFunction<StringBuilder, String, StringBuilder> fmt, Function<StringBuilder, StringBuilder> separator) {
            final var sb     = new StringBuilder();
            final var values = getValues();
            
            if (values == null || values.length == 0) {
                return fmt.apply(sb, null);
            }
            
            var length = values.length;
            
            for (String v : values) {
                fmt.apply(sb, v == null || "".equals(v) ? "null" : v);
                
                if (--length > 0) separator.apply(sb);
            }
            
            return sb;
        };
        
        static String formatPropertiesAsJSON(boolean asCloudEvent) {
            final StringBuilder json = new StringBuilder();
            
            // format the property list as JSON Object...
            
            final var values = SystemProperties.values();
            
            var nValues = values.length;
            
            if (asCloudEvent) {
            	json.append("{ specversion : \"1.0\",");
            	json.append(" type : \""            + UsageLogger.class.getCanonicalName()       + "\",");
            	json.append(" source : \"urn:uuid:" + SystemProperties.JVM_UUID.getValue()       + "\",");
            	json.append(" id : \""              + SystemProperties.JVM_PID.getValue()        + "\",");
            	json.append(" time : \""            + SystemProperties.JVM_START_TIME.getValue() + "\",");
            	
            	json.append(" datacontenttype : \"application/json\",");
            	
                json.append(" data : ");
            }
            
            json.append("{ ");

            for (SystemProperties sp : values) {
                json.append(sp.formatPropertyAndValuesAsJSON());
                
                if (--nValues > 0) json.append(", ");
            }
            
            json.append(" }");
            
            if (asCloudEvent) {
            	json.append("}");
            }
            
            return json.toString();
        }
        
        
        // member(s)

        private final Function<SystemProperties, String[]> values;  // function to extract values ...
        
    }
    
    private static String getPropertyPrivileged(final String property) {
        return getPropertyPrivileged(property, null);
    }

    private static String getPropertyPrivileged(final String property, final String defaultValue) {
        return getPrivileged(()-> System.getProperty(property, defaultValue));
    }

    private static String getEnvPrivileged(final String envName) {
        return getPrivileged(() -> System.getenv(envName));
    }
    
    private static String getPrivileged(final Supplier<String> supplier) {
        return AccessController.doPrivileged((PrivilegedAction<String>)() -> supplier.get());
    }

    /*
     * This method will try to find a usagelogger.properties file in several
     * different locations. 
     * The following places will be searched, in order: 
     * 
     * 1. The path specified by the System.property 'jdk.usagelogger.config.file' 
     * 2. The path specified, if not null, by the environment variable 'JDK_USAGELOGGER_CONFIG_FILE'
     * 2. The default path ${java.home}/conf/management/usagelogger.properties
     * 3. A pre-defined path that is os specific, *and* global in effect (i.e for all JREs installed)
     */
    private static File getConfigFilePrivileged() {
        return AccessController.doPrivileged((PrivilegedAction<File>)() -> {
            File confFile = null;
            
        	final String[] paths = {
        			System.getProperty(JDK_UL_PROPERTY_CONFIG_FILE_PATH),
        			System.getenv(JDK_UL_PROPERTY_CONFIG_FILE_PATH.toUpperCase().replace('.', '_')), // NEW: try env var...
        			System.getProperty("java.home") + File.separator + "conf" + File.separator + "management" + File.separator + JDK_UL_DEFAULT_CONFIG_FILENAME,
        			getOSSpecificConfigFilePath()
        	};
        	
        	for (final String path : paths) {
        		if (path != null) {
        			confFile = new File(path);

        			if (confFile != null && confFile.exists() && confFile.canRead())
        				break;
        			else 
        				confFile = null;
        		}
        	}

        	return confFile;
        });
    }

    private static String getOSSpecificConfigFilePath() {
        var os = getPropertyPrivileged("os.name");
        
        if (os != null) {
             os = os.toLowerCase().trim();

            if (os.startsWith("sunos")) {
                return "/etc/java/conf/management/" + JDK_UL_DEFAULT_CONFIG_FILENAME;
            } else if (os.startsWith("mac")) {
                return "/Library/Application Support/Java/conf/management/" + "" + JDK_UL_DEFAULT_CONFIG_FILENAME;
            } else if (os.startsWith("win")) {
                final var programFilesPath = getEnvPrivileged("ProgramFiles");

                return (programFilesPath == null) ? null : programFilesPath + "\\Java\\conf\\management\\" + JDK_UL_DEFAULT_CONFIG_FILENAME;
            } else if (os.startsWith("linux")) {
                return "/etc/java/conf/management/" + JDK_UL_DEFAULT_CONFIG_FILENAME;
            }
        }
        
        return null;
    }

    // Retrieve and possibly expand the logToFile property:
    // it may begin with "${user.home}". It may also contain "${user.name}", "${user.dir}" "${jvm.pid}", "${jvm.uuid}" or "${java.io.tmpdir"}
    
    private static File processFilename(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        } else {
            // separate path from file...
        	
            final var idx = path.lastIndexOf(File.separator) + 1;
        	
            var fileName = path.substring(idx); // extract file...
        	
            path = path.substring(0, idx); // truncate path...
        	
	    if (path.startsWith(USER_HOME_VAR)) {
		path.replace(USER_HOME_VAR, SystemProperties.USER_HOME.getValue());
	    }

            final var p = Pattern.compile(VARIABLE_PATTERN);
                        
            path = p.matcher(path).replaceAll((mr) -> { 
            	final var v = mr.group();
                
                String ret = null;
                
                switch (v) {
                    case HOSTNAME_VAR:     ret = SystemProperties.HOSTNAME.getValue();
                    break;
                    
                    case DATE_VAR:         ret = DateTimeFormatter.ISO_LOCAL_DATE.format(startTime);
                    break;
                    
                    case JAVA_VERSION_VAR: ret = SystemProperties.JAVA_VERSION.getValue();
                    break;
                    
                    case JAVA_VENDOR_VAR:  ret = SystemProperties.JAVA_VENDOR.getValue();
                    break;
                    
                    //case USER_DIR_VAR:       ret = SystemProperties.USER_DIR.getValue();
                    //break;
                    
                    // TODO: anymore?
                }
                return Matcher.quoteReplacement(ret == null ? v : ret); // no value - no substitution...
            });
            
            // TODO: some substitutions would result in an "unshared" log file... might be useful to capture this and pass to getLogFile()
            fileName = p.matcher(fileName).replaceAll((mr) -> {
                final var v = mr.group();
                
                String ret = null;
                
                switch (v) {                   
                    case USER_NAME_VAR:    ret = SystemProperties.USER_NAME.getValue(); // unshared/unique?
                    break;
                    
                    case DATE_VAR:         ret = DateTimeFormatter.ISO_LOCAL_DATE.format(startTime);
                    break;
                    
                    case TIME_VAR:         ret = TIME_FORMATTER.format(startTime);
                    break;
                    
                    case JAVA_VERSION_VAR: ret = SystemProperties.JAVA_VERSION.getValue();
                    break;
                    
                    case JAVA_VENDOR_VAR:  ret = SystemProperties.JAVA_VENDOR.getValue();
                    break;
            
                    case JVM_PID_VAR:      ret = SystemProperties.JVM_PID.getValue(); // unshared/unique
                    break;
                    
                    case JVM_UUID_VAR:     ret = SystemProperties.JVM_UUID.getValue(); // unshared/unique
                    break;
                }
 
                return Matcher.quoteReplacement(ret == null ? v : ret); // no value - no substitution...
            });
            
            // TODO - what if path is illegal?
            
            final var file = new File(path, fileName); // reassemble path & var substituted file name...
            
            if (!file.isAbsolute()) {
                printVerbose("UsageLogger: relative path disallowed.");
                return null;
            } else if (file.exists()) {
                if (!file.canWrite()) {
                    printDebug("not writeable: " + file.getAbsolutePath());
                    return null;
                }
            } else {
            	final File parent = file.getParentFile();
            	final var  pp     = parent.getAbsolutePath();
            	
            	if (!parent.exists()) {
            		printDebug("parent path does not exist: " + pp);
            		return null;
            	}
            	if (!parent.canWrite()) {
                    printDebug("parent directory not writeable: " + pp);
                    return null;
                } 
            }
            
            return file;
        }
    }

    /**
     * Convenience routine, for getting a long value from a Properties
     * object.
     */
    private static long getPropertyValueLong(Properties props, String propName) {
        String propertyValue = props.getProperty(propName, "");
        
        if (!propertyValue.isEmpty()) try {
                return Long.parseLong(propertyValue);
        } catch (NumberFormatException ignored) {
            printVerbose("UsageLogger: bad value: " + propName);
        }

        return -1;
    }

    /**
     * Convenience routine, for getting a boolean value from a Properties
     * object.
     */
    private static boolean getPropertyValueBoolean(Properties props, String propName, boolean defaultValue) {
        String propertyValue = props.getProperty(propName, "");
        
        if (!propertyValue.isEmpty()) {
            return Boolean.parseBoolean(propertyValue);
        }
        
        return defaultValue;
    }
    
    private static String[] getAdditionalProperties(Properties props) {
        // additionalProperties, if set, is a comma-separated list of properties
        // to retrieve and log.
        String propertyValue = props.getProperty(JDK_UL_ADDITIONALPROPERTIES, "");
        
        return (propertyValue.isEmpty()) ? new String[0] : propertyValue.split("\\s*,\\s*"); // note split is whitespace, comma separated
    }

    private static void printVerbose(String msg) {
        if (verbose) {
            System.err.println(msg);
            System.err.flush();
        }
    }

    private static void printDebug(String msg) {
        if (debug) {
            System.err.println(msg);
            System.err.flush();
        }
    }

    private static void printDebugStackTrace(Throwable t) {
        if (debug) {
            t.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
	private static  String[] getInputArguments() {
    	return AccessController.doPrivileged((PrivilegedAction<String[]>)(() -> { 
    		if (runtime != null) try {
				return ((List<String>)(runtime.getClass().getMethod("getInputArguments", (Class<?>[]) null).invoke(runtime, (Object[]) null))).toArray(new String[] {});
    		} catch (Exception e) {
    		    // ignore... for now.
    		}
    		return new String[] { null };
    	}));
    	
//        return AccessController.doPrivileged(() -> {
//                //return ManagementFactory.getRuntimeMXBean().getInputArguments().toArray(new String[] {}); // TODO - replace this!!!!! 
//                //return jdk.internal.misc.VM.getRuntimeArguments(); // TODO see if this works from within java.base!!!! // TODO
//        	
//        	    return new String[] { null };
//            }
//        );
    }
        
    private UsageLogger() {}

    /**
     * Build the tracking message.
     */
    private static String buildTextLogMessage() {
        StringBuilder message = new StringBuilder();

        message.append(getRuntimeDetails()); // don't quote, it's a set of fields
        message.append("\n"); // end of record

        return message.toString();
    }

    private static String getRuntimeDetails() {
        final var m      = new StringBuilder();
        final var values = SystemProperties.values();
        var nValues      = values.length;

        final Function<StringBuilder, StringBuilder> space     = (sb) -> sb.append(' ');
        final Function<StringBuilder, StringBuilder> separator = (sb) -> sb.append(UsageLogger.separator);

        // iterate across Properties in order to construct text log msg...
        
        for (SystemProperties sp : SystemProperties.values()) {
            switch (sp) {
                case ADDITIONAL_PROPERTIES: // special processing for "additional properties" 
                	// Note: "elvis" operator is intended to deal with the case when there are or are not any "additional properties" specified ...
                    appendWithQuotes(m, sp.FormatPropertyValues((sb, v) -> { return addQuotesFor(sb, (v != null ? v + "=" + getPropertyPrivileged(v) : "null"), " ", innerQuote);}, space).toString());
                break;

                case JVM_ARGS:
                    appendWithQuotes(m, sp.FormatPropertyValues((sb, v) -> addQuotesFor(sb, v, " ", innerQuote), space).toString());
                break;

                default: 
                    m.append(sp.FormatPropertyValues((sb, v) -> appendWithQuotes(sb, v),  separator));
                break;
            };

            if (--nValues > 0) separator.apply(m);                   
        }

        return m.toString();
    }

    /**
     * Append to a StringBuilder: add the given String, but add the defined
     * QUOTE before and after.
     * Apply any maxFieldSize limit to the given String before appending.
     * Make any existing quote sequences in the given String into "doubled" quotes.
     */
    private static StringBuilder appendWithQuotes(StringBuilder sb, String s) {
        sb.append(quote);
        s = s.replace(quote, quote + quote);
        if (!s.isEmpty()) {
            sb.append(s);
        } else {
            sb.append(" "); // empty field, avoids interpreting as double-quote
        }
        sb.append(quote);
        
        return sb;
    }

    /**
     * If the given String contains any given target String, return the entire
     * original String surrounded by the given quote String. Make any existing
     * quote sequences into "doubled" quotes. Otherwise return the String
     * unchanged.
     */
    private static StringBuilder addQuotesFor(StringBuilder m, String item, String target, String quote) {
        // Null check as this is called with the additionalProperties
        // values, which could be null.
        
        if (item == null) {
            return m;
        }
        item = item.replace(quote, quote + quote);
        if (item.indexOf(target) >= 0) {
            item = quote + item + quote;
        }
        return m.append(item);
    }

    // registered and called by JFR itself, see jdk.jfr.internal.instrument.JDKEvents for details...

    public static final Runnable emitUsageLogEvent = new Runnable() {
        @Override
        public void run() {
            final var lue = new UsageLogEvent();

            lue.begin();

            lue.startTime   = SystemProperties.JVM_START_TIME.getValue();
            lue.hostname    = SystemProperties.HOSTNAME.getValue();
            lue.ipAddress   = SystemProperties.IP_ADDRESS.getValue();

            lue.pid         = SystemProperties.JVM_PID.getValue();
            lue.uuid        = SystemProperties.JVM_UUID.getValue();

            lue.javaHome    = SystemProperties.JAVA_HOME.getValue();
            lue.javaVersion = SystemProperties.JAVA_VERSION.getValue();
            lue.javaVendor  = SystemProperties.JAVA_VENDOR.getValue();
            lue.jvmVersion  = SystemProperties.JAVA_VM_VERSION.getValue();
            lue.jvmVendor   = SystemProperties.JAVA_VM_VENDOR.getValue();

            lue.javaHome    = SystemProperties.JAVA_HOME.getValue();

            final BiFunction<StringBuilder, String, StringBuilder> appendWithQuotes = (sb, v) -> appendWithQuotes(sb, v);
            final Function<StringBuilder, StringBuilder>           separator        = (sb)    -> sb.append(UsageLogger.separator);

            lue.javaArgs    = SystemProperties.JAVA_ARGUMENTS.FormatPropertyValues(appendWithQuotes, separator).toString();

            lue.vmArgs      = SystemProperties.JVM_ARGS.FormatPropertyValues(appendWithQuotes, separator).toString();

            lue.osName      = SystemProperties.OS_NAME.getValue();
            lue.osVersion   = SystemProperties.OS_VERSION.getValue();
            lue.osArch      = SystemProperties.OS_VERSION.getValue();

            lue.classpath   = SystemProperties.JAVA_CLASS_PATH.getValue();

            lue.modulePath      = SystemProperties.JDK_MODULE_PATH.getValue();
            lue.mainModule      = SystemProperties.JDK_MODULE_MAIN.getValue();
            lue.moduleMainClass = SystemProperties.JDK_MODULE_MAIN_CLASS.getValue();

            lue.userName    = SystemProperties.USER_NAME.getValue();
            lue.userDir     = SystemProperties.USER_DIR.getValue();
            //lue.userHome    = SystemProperties.USER_HOME.getValue();

            lue.commit();
        }
    };

    private static void logToURL(String url) {
        // note we use the original HTTP APIs to avoid cross-module dependencies, which use of the new APIs would result in...

        HttpURLConnection httpConn = null;

        if (url == null) return;

        try {
            
            // before we attempt to convert the string to an URL lets perform any variable substitution...
            
            final var m    = Pattern.compile(VARIABLE_PATTERN).matcher(url);
            final var utf8 = Charset.forName("UTF-8");
            
            url = m.replaceAll((mr) -> {
                final var v = mr.group();
                
                String ret = null;
                
                switch (v) {
                    case HOSTNAME_VAR:     ret = SystemProperties.HOSTNAME.getValue();
                    break;
                    
                    case IP_ADDRESS_VAR:   ret = SystemProperties.IP_ADDRESS.getValue();
                    break;
                    
                    case OS_NAME_VAR:      ret = SystemProperties.OS_NAME.getValue();
                    break;
                    
                    //case USER_NAME_VAR:    ret = SystemProperties.USER_NAME.getValue();
                   // break;
                    
                    case DATE_VAR:         ret = DateTimeFormatter.ISO_LOCAL_DATE.format(startTime);
                    break;
                    
                    case TIME_VAR:         ret = TIME_FORMATTER.format(startTime);
                    break;
                    
                    case JAVA_VERSION_VAR: ret = SystemProperties.JAVA_VERSION.getValue();
                    break;
                    
                    case JAVA_VENDOR_VAR:  ret = SystemProperties.JAVA_VENDOR.getValue();
                    break;
            
                    case JVM_PID_VAR:      ret = SystemProperties.JVM_PID.getValue();
                    break;
                    
                    case JVM_UUID_VAR:     ret = SystemProperties.JVM_UUID.getValue();
                    break;
                }
 
                return Matcher.quoteReplacement(ret == null ? v : URLEncoder.encode(ret, utf8)); // no value - no substitution...
            });
            
            // note: var subs may result in a malformed url ... 
            
            httpConn = (HttpURLConnection) new URL(url).openConnection();
            
            if (verbose) printVerbose("logToURL: " + httpConn.getURL().toExternalForm());
            
            httpConn.setAllowUserInteraction(false);
            httpConn.setDefaultUseCaches(false);
            httpConn.setDoOutput(true);
            httpConn.setRequestMethod("POST");
            httpConn.setRequestProperty("Content-Type", "application/cloudevents+json");
            httpConn.setRequestProperty("User-Agent", "JDK/" + SystemProperties.JAVA_VENDOR + "-" + SystemProperties.JAVA_VERSION + " ( " + SystemProperties.OS_NAME + "; " + SystemProperties.OS_VERSION + "; " + SystemProperties.OS_ARCH + ")");

            // cloudevents request properties...
            
            httpConn.setRequestProperty("ce-specversion", "1.0");
            httpConn.setRequestProperty("ce-type",        UsageLogger.class.getCanonicalName());
            httpConn.setRequestProperty("ce-time",        SystemProperties.JVM_START_TIME.getValue());
            httpConn.setRequestProperty("ce-source",      "urn:uuid:" + SystemProperties.JVM_UUID.getValue());
            httpConn.setRequestProperty("ce-id",          SystemProperties.JVM_PID.getValue());
            
            final var props = SystemProperties.formatPropertiesAsJSON(true); // TODO format as cloudevent payload
            
            printDebug(props);
            
            final var bytes = props.getBytes();
        
            httpConn.setRequestProperty("Content-Length", Integer.toString(bytes.length));

            httpConn.connect();

            final var os = httpConn.getOutputStream();

            os.write(bytes, 0, bytes.length);
            
            os.flush();
            os.close();

            final var resp = httpConn.getResponseCode();
            final var msg  = httpConn.getResponseMessage();

            switch (resp) {
                case HttpURLConnection.HTTP_OK:
                case HttpURLConnection.HTTP_CREATED:
                case HttpURLConnection.HTTP_ACCEPTED:
                case HttpURLConnection.HTTP_NO_CONTENT: 
                     printDebug("logToURL response code: " + resp);
                break;
                
                default:
                    printDebug("bad response: " + resp + " " + msg);
            }
        } catch (IOException ioe) {
            printDebugStackTrace(ioe);
        } finally {
            if (httpConn!= null) httpConn.disconnect();
        }
    }
    
    private static FileChannel getLogfile(final Path path, final boolean shared) throws IOException { // called from doPrivileged blk
    	FileChannel fc = null;

    	/*
    	 * all of the following is intended to 'force' the creation of a "share-able" log file should the file not exist... or have the wrong perms
    	 */
    	
    	final var perms   = PosixFilePermissions.fromString(shared ? "rw-rw-rw-" : "rw-r--r--"); // needed to share a log file across JVM users...
    	final var options = Set.of(StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
		
    	try { // 1st try to optimistically open/create the log file for write append...
    		fc = FileChannel.open(path, options, PosixFilePermissions.asFileAttribute(perms));	
    	} catch (IllegalArgumentException | UnsupportedOperationException e) {
    		// ok so we failed to open it lets attempt to create it... w/o posix attrs
    		
    		fc = FileChannel.open(path, options);
    	}
    	
    	// lets check the permissions - if we have Posix fs... its racy but only results in spurious perms writes worst case.
		
		if (fc != null && Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView.class) ) {
			final var view  = Files.getFileAttributeView(path, PosixFileAttributeView.class, new LinkOption[] { });
			final var attrs = view.readAttributes();
			
			if (!attrs.permissions().equals(perms)) // if the file perms are "wrong" attempt to set them up properly - logs need shared r/w
				view.setPermissions(perms);
		} else {
			printDebug("unable to open/create file, or no ability to set access permissions");
		}
		
    	return fc;
    }

    private static void logToFile(File usageLoggerFile) {  // called from doPrivileged(...) blk
        printDebug("UsageLogger: logToFile");

        final var path = usageLoggerFile.toPath();

        printDebug("UsageLogger file: " + usageLoggerFile.getAbsolutePath());
       
        try (final FileChannel fc     = getLogfile(path, true);
             final Writer      writer = Channels.newWriter(fc, StandardCharsets.UTF_8.name())) {

            final var logText = usageLoggerFile.getName().endsWith(".json") ? SystemProperties.formatPropertiesAsJSON(false) : buildTextLogMessage();

            writer.write(logText, 0, logText.length());

            writer.flush();

            printDebug(logText);

            //fc.force(false); ?

            printVerbose("UsageLogger: logged to file: " + usageLoggerFile.getAbsolutePath());
            
            //final var attrs = Files.getFileAttributeView(path, PosixFileAttributeView.class);
            
            //printVerbose(attrs.readAttributes().permissions().toString());
        } catch (Throwable t) {
            printVerbose("UsageLogger: error in writing to file.");
            printDebugStackTrace(t);
        } finally {
        	//
        }
    }
    
    private static void logToUDS(String uds) { // UDS
    	try (final var sc = SocketChannel.open(StandardProtocolFamily.UNIX)) {
			final var saddr = UnixDomainSocketAddress.of(uds);
			
			sc.connect(saddr);
			
			if (sc.finishConnect()) sc.write(ByteBuffer.wrap((uds.endsWith(".json") ? SystemProperties.formatPropertiesAsJSON(true) : buildTextLogMessage()).getBytes()));
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			if (debug) e.printStackTrace();
		}
    }
    
    /*
     * generate a name (type 3) UUID for this JVM invocation using some of the System property values that uniquely identify it...
     */
    public final static UUID getJvmUuid() { 
    	final var hash = new StringBuilder();
    	
    	final var sysProps = new SystemProperties[] {
    			SystemProperties.JVM_START_TIME,
    			SystemProperties.HOSTNAME,
    			SystemProperties.IP_ADDRESS,
    			SystemProperties.JVM_PID,
    			SystemProperties.JAVA_VENDOR,
    			SystemProperties.JAVA_VERSION,
    			SystemProperties.JAVA_ARGUMENTS,
    			SystemProperties.JAVA_VM_VERSION,
    			SystemProperties.JAVA_ARGUMENTS,
    			SystemProperties.JVM_ARGS,
    			SystemProperties.JAVA_CLASS_PATH,        // maybe null
    			SystemProperties.JDK_MODULE_PATH,        // ditto
    			SystemProperties.JDK_MODULE_UPGRADE_PATH,// ditto
    			SystemProperties.JDK_MODULE_MAIN,        // ditto
    			SystemProperties.JDK_MODULE_MAIN_CLASS,  // ditto
    			SystemProperties.USER_NAME,
    			SystemProperties.USER_DIR
    	};

		for (SystemProperties sp : sysProps) {
    		final var v = sp.getValue();
    		
    		if (v != null) hash.append(v);
    	}
		
    	return UUID.nameUUIDFromBytes(hash.toString().getBytes()); // nameUUIDFormBytes calculates MD5 hash over string...
    };

    /**
     * Perform UsageTracking with the specified details.
     *
     */
    
    private static enum RunMode {
        SYNCHRONOUS,
        ASYNCHRONOUS,
        DAEMON, // default
    };
    
    public static void logUsage() {
        printDebug("UsageLogger.run");
        
        if (!usageLoggerProperties.isEmpty()) { // iff not empty then we successfully loaded the config properties...
        	final var runMode = RunMode.valueOf(usageLoggerProperties.getProperty(JDK_UL_PROPERTY_RUN_MODE, RunMode.DAEMON.name()).toUpperCase());
            
        	// who doesn't love nested lambda's?
        	
        	final Runnable logger = () -> AccessController.doPrivileged((PrivilegedAction<Void>)() -> {
        		try {
        			final var uds = usageLoggerProperties.getProperty(JDK_UL_LOGTOUDS, null); // UDS

        			if (uds != null) {
        				logToUDS(uds);
        			}

        			// process logging to file...

        			final var usageLoggerFile = processFilename(usageLoggerProperties.getProperty(JDK_UL_LOGTOFILE, null));

        			if (usageLoggerFile != null) {
        				if (logFileMaxSize >= 0 && usageLoggerFile.length() >= logFileMaxSize) {
        					printVerbose("UsageLogger: log file size exceeds maximum.");
        				} else
        					logToFile(usageLoggerFile);
        			} else 
        				printDebug("invalid log file?");

        			// now process logToURL...

        			final var url = usageLoggerProperties.getProperty(JDK_UL_LOGTOURL, null);

        			if (url != null) {
        				logToURL(url);
        			}

        		} catch(Throwable t) {
        			printDebug(t.getMessage());
        			printDebugStackTrace(t);
        		}
        		return (Void)null;
        	});
        	
        	if (runMode != RunMode.SYNCHRONOUS) {

        		// Ensure we are in the root thread group: needs to be
        		// verified that this is still required.

        		ThreadGroup tg = Thread.currentThread().getThreadGroup();

        		while (tg.getParent() != null) {
        			tg = tg.getParent();
        		}

        		final var thread = new Thread(tg, logger); // "root" process group... 
        		
        		if (runMode == RunMode.DAEMON) {
        			printVerbose("UsageLogger: running asynchronous daemon.");

        			printDebug("daemon");

        			thread.setDaemon(true);
        			
        			thread.setName("UsageLogger-Daemon");
        		} else { // RunMode.ASYNCHRONOUS
        			printVerbose("UsageLogger: running asynchronous.");

        			printDebug("asynchronous");
        			
        			thread.setName("UsageLogger");
        		}
        		
        		thread.start();
        	} else {
        		printVerbose("UsageLogger: running synchronous.");

        		logger.run(); // simply run the logger in this thread...
        	}
        }
    }
}
