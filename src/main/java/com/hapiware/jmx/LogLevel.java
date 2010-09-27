package com.hapiware.jmx;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeMBeanException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import sun.jvmstat.monitor.HostIdentifier;
import sun.jvmstat.monitor.MonitorException;
import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.MonitoredVm;
import sun.jvmstat.monitor.MonitoredVmUtil;
import sun.jvmstat.monitor.VmIdentifier;
import sun.management.ConnectorAddressLink;

/**
 * LogLevel is a small command line utility to show and change level of Java Loggers
 * {@link java.util.logging.Logger} without restarting the target JVM.
 * 
 * @author hapi
 *
 */
public class LogLevel
{
	private static final String JAVA_LOGGING_NAME = "java.util.logging:type=Logging";
	private static final String LOG4J_LOGGING_NAME = "com.hapiware.log4j:type=Logging";

	
	public static void main(String[] args)
	{
		// Checks first the correct number of arguments.
		if(args.length == 0 || args.length > 5)
			usageAndExit(-1);
		if((args[0].equalsIgnoreCase("j") || args[0].equalsIgnoreCase("jobs")) && args.length != 1)
			usageAndExit(-1);
		if(
			(
				args[0].equalsIgnoreCase("l")
				|| args[0].equalsIgnoreCase("list")
				|| args[0].equalsIgnoreCase("p")
				|| args[0].equalsIgnoreCase("parent")
			)
			&& (args.length < 3 || args.length > 4)
		)
			usageAndExit(-1);
		if(
			(args[0].equalsIgnoreCase("s") || args[0].equalsIgnoreCase("set"))
			&& (args.length < 4 || args.length > 5)
		)
			usageAndExit(-1);
		if(
			args[0].equalsIgnoreCase("-?") ||
			args[0].equalsIgnoreCase("-h") ||
			args[0].equalsIgnoreCase("-help") ||
			args[0].equalsIgnoreCase("--help")
		)
			usageAndExit(0);
		
		if(args[0].equalsIgnoreCase("--version"))
			showVersionAndExit();
		

		// Shows JVMs and their PIDs.
		if(args[0].equalsIgnoreCase("j") || args[0].equalsIgnoreCase("jobs")){
			listJvms();
			return;
		}
		
		// Lists logging levels.
		if(args[0].equalsIgnoreCase("l") || args[0].equalsIgnoreCase("list")) {
			try {
				Integer pid = Integer.valueOf(args[1]);
				if(args.length == 3)
					showLevels(pid, null, args[2]);
				else
					showLevels(pid, args[2], args[3]);
			}
			catch(NumberFormatException ex) {
				System.out.println(args[1] + " was not recognized as PID.");
				usageAndExit(-1);
			}
			return;
		}
		
		// Lists parents.
		if(args[0].equalsIgnoreCase("p") || args[0].equalsIgnoreCase("parent")) {
			try {
				Integer pid = Integer.valueOf(args[1]);
				if(args.length == 3)
					showParents(pid, null, args[2]);
				else
					showParents(pid, args[2], args[3]);
			}
			catch(NumberFormatException ex) {
				System.out.println(args[1] + " was not recognized as PID.");
				usageAndExit(-1);
			}
			return;
		}
		
		
		// Sets logging levels
		if(args[0].equalsIgnoreCase("s") || args[0].equalsIgnoreCase("set")) {
			try {
				Integer pid = Integer.valueOf(args[1]);
				if(args.length == 4)
					setLevels(pid, null, args[2], args[3]);
				else
					setLevels(pid, args[2], args[3], args[4]);
			}
			catch(NumberFormatException ex) {
				System.out.println(args[1] + " was not recognized as PID.");
				usageAndExit(-1);
			}
			return;
		}
		
		System.out.println("Command (" + args[0] + ") not recognized.");
		usageAndExit(-1);
	}
	
	private static void showVersionAndExit()
	{
		final String propertyFile = "version.properties";
		final String property = "version";
		Properties p = new Properties();
		try {
			InputStream is =
				Thread.currentThread().getContextClassLoader().getResourceAsStream(propertyFile);
			if(is == null) {
				System.out.println(
					"Version information unavailable due to missing property file: " + propertyFile
				);
				System.exit(-1);
			}
			p.load(is);
			System.out.println("  Version: " + p.getProperty(property));
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		System.exit(0);
	}

	private static void usageAndExit(int status)
	{
		final String cmd = "java -jar loglevel.jar";
		System.out.println("Description: Lists and sets the logging level of Java Loggers (java.util.logging.Logger) on the run.");
		System.out.println("             Optionally log4j is also supported but it requires java.util.logging.LoggingMXBean");
		System.out.println("             interface to be implemented for log4j. See http://www.hapiware.com/jmx-tools.");
		System.out.println();
		System.out.println("Usage: " + cmd + " [-? | -h | -help | --help | --version]");
		System.out.println("       " + cmd + " COMMAND [ARGS]");
		System.out.println();
		System.out.println("       COMMAND:");
		System.out.println("          j");
		System.out.println("          jobs");
		System.out.println("              Shows PIDs and names of running JVMs (jobs). PID starting with");
		System.out.println("              an asterisk (*) means that JMX agent is not runnig on that JVM.");
		System.out.println("              Start the target JVM with -Dcom.sun.management.jmxremote or");
		System.out.println("              if you are running JVM 1.6 or later use startjmx service.");
		System.out.println();
		System.out.println("          l PID [j|J|4] PATTERN [| root]");
		System.out.println("          list PID [j|J|4] PATTERN [| root]");
		System.out.println("              Lists current logging levels for all loggers matching PATTERN");
		System.out.println("              (Java RE) in a JVM process with PID. 'root' as PATTERN lists");
		System.out.println("              only the root logger(s). Java loggers are identified by (J) prefix");
		System.out.println("              and log4j loggers by (4). After PID argument may be an optional");
		System.out.println("              argument to focus 'list' command only to either logger type.");
		System.out.println("              J or j for Java logger and 4 for log4j logger.");
		System.out.println();
		System.out.println("          p PID [j|J|4] PATTERN");
		System.out.println("          parent PID [j|J|4] PATTERN");
		System.out.println("              Shows loggers and their direct parent loggers matching PATTERN");
		System.out.println("              (Java RE) in a JVM process with PID. Java loggers are identified");
		System.out.println("              by (J) prefix and log4j loggers by (4). After PID argument may");
		System.out.println("              be an optional argument to focus 'parent' command only to either");
		System.out.println("              logger type. J or j for Java logger and 4 for log4j logger.");
		System.out.println();
		System.out.println("          s PID [j|J|4] PATTERN [| root] LEVEL [| null]");
		System.out.println("          set PID [j|J|4] PATTERN [| root] LEVEL [| null]");
		System.out.println("              Sets a new logging level LEVEL for all loggers matching PATTERN");
		System.out.println("              (Java RE) in a JVM process with PID. 'root' as PATTERN sets only");
		System.out.println("              the root logger(s). LEVEL is a string representting a new logging");
		System.out.println("              level. Value 'null' is accepted to set the logging level to follow");
		System.out.println("              a parent logger's logging level. Java loggers are identified by");
		System.out.println("              (J) prefix and log4j loggers by (4). After PID argument may be");
		System.out.println("              an optional argument to focus 'set' command only to either logger");
		System.out.println("              type. J or j for Java logger and 4 for log4j logger.");
		System.out.println();
		System.out.println("Examples:");
		System.out.println("    " + cmd + " -?");
		System.out.println("    " + cmd + " jobs");
		System.out.println("    " + cmd + " l 50001 ^.+");
		System.out.println("    " + cmd + " list 50001 root");
		System.out.println("    " + cmd + " p 50001 ^com\\.hapiware\\..*Worker.*");
		System.out.println("    " + cmd + " set 50001 j ^com\\.hapiware\\..*Worker.* INFO");
		System.out.println("    " + cmd + " set 50001 4 .*Test null");
		System.out.println();
		System.exit(status);
	}
	
	private static void showConnectionErrorAndExit(Integer pid)
	{
		System.out.println("Cannot connect to " + pid + ". JMX agent is not running.");
		System.out.println(
			"Start the target JVM with -Dcom.sun.management.jmxremote or" 
			+ " if you are running JVM 1.6 or later use startjmx service."
		);
		System.exit(-1);
	}
	
	private static void listJvms()
	{
		try {
			MonitoredHost mh = MonitoredHost.getMonitoredHost(new HostIdentifier((String)null)); 
			Set<?> vms = mh.activeVms();
			for(Iterator<?> it = vms.iterator(); it.hasNext(); /* empty */) {
				Integer pid = (Integer)it.next();
				MonitoredVm vm = mh.getMonitoredVm(new VmIdentifier(pid.toString()));
				String commandLine = MonitoredVmUtil.commandLine(vm);
				if(commandLine.toLowerCase().contains("loglevel"))
					continue;
				
				String serviceUrl = ConnectorAddressLink.importFrom(pid);
				System.out.println((serviceUrl == null ? "*" : "")  + pid + " " + commandLine);
				vm.detach();
			}
		}
		catch(URISyntaxException e) {
			e.printStackTrace();
		}
		catch(MonitorException e) {
			e.printStackTrace();
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		catch(NullPointerException e) {
			e.printStackTrace();
		}
	}
	
	private static MBeanServerConnection getConnection(Integer pid)
	{
		MBeanServerConnection connection = null;
		try {
			String serviceUrl = ConnectorAddressLink.importFrom(pid);
			if(serviceUrl == null)
				return null;
			
			JMXServiceURL jmxUrl = new JMXServiceURL(serviceUrl);
			JMXConnector connector = JMXConnectorFactory.connect(jmxUrl);
			connection = connector.getMBeanServerConnection();
		}
		catch(IOException e) {
			System.out.println("Process " + pid + " not found.");
			System.exit(-1);
		}
		return connection;
	}
	
	private static String checkLevel(String level)
	{
		if(level == null || level.trim().length() == 0)
			level = "Not defined.";
		return level;
	}
	
	private static String checkLoggerName(String loggerName)
	{
		if(loggerName == null || loggerName.trim().length() == 0)
			loggerName = "root";
		return loggerName;
	}
	
	private static String getLoggerLevel(
		MBeanServerConnection connection,
		ObjectName name,
		String loggerName
	)
		throws 
			InstanceNotFoundException,
			MBeanException,
			ReflectionException,
			IOException
	{
		String level = 
			(String)connection.invoke(
				name,
				"getLoggerLevel",
				new String[] { loggerName.equals("root") ? "" : loggerName },
				new String[] { "java.lang.String" }
			);
		return checkLevel(level);
	}
	
	private static String getParentLoggerName(
		MBeanServerConnection connection,
		ObjectName name,
		String loggerName
	)
		throws 
			InstanceNotFoundException,
			MBeanException,
			ReflectionException,
			IOException
	{
		return
			(String)connection.invoke(
				name,
				"getParentLoggerName",
				new String[] { loggerName },
				new String[] { "java.lang.String" }
			);
	}
	
	private static void setLoggerLevel(
		MBeanServerConnection connection,
		ObjectName name,
		String loggerName,
		String level
	)
		throws
			InstanceNotFoundException,
			MBeanException,
			ReflectionException,
			IOException
	{
		connection.invoke(
			name,
			"setLoggerLevel",
			new String[] { loggerName, level.equals("null") ? null : level },
			new String[] { "java.lang.String", "java.lang.String" }
		);
	}
	
	private static void showLevels(Integer pid, String loggerType, String loggerNamePattern)
	{
		try {
			MBeanServerConnection connection = getConnection(pid);
			if(connection == null)
				showConnectionErrorAndExit(pid);
			
			Pattern pattern = Pattern.compile(loggerNamePattern);
			Map<String, ObjectName> names = createObjectNames(loggerType);
			for(Iterator<String> it = names.keySet().iterator(); it.hasNext(); /* empty */) {
				String loggerTypeKey = it.next();
				ObjectName name = names.get(loggerTypeKey);
				try {
					if(loggerNamePattern.equals("root")) {
						System.out.println(
							loggerTypeKey + " root : " + getLoggerLevel(connection, name, "")
						);
					}
					else {
						String[] loggerNames =
							(String[])connection.getAttribute(name, "LoggerNames");
						for(String ln : loggerNames) {
							if(pattern.matcher(ln).matches())
								System.out.println(
									loggerTypeKey + " " + ln + " : " + getLoggerLevel(connection, name, ln)
								);
						}
					}
				}
				catch(InstanceNotFoundException e) {
					// Does nothing. Just skips the loop.
				}
			}
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		catch(MalformedObjectNameException e) {
			e.printStackTrace();
		}
		catch(NullPointerException e) {
			e.printStackTrace();
		}
		catch(ReflectionException e) {
			e.printStackTrace();
		}
		catch(AttributeNotFoundException e) {
			e.printStackTrace();
		}
		catch(MBeanException e) {
			e.printStackTrace();
		}
	}
	
	private static void showParents(Integer pid, String loggerType, String loggerNamePattern)
	{
		try {
			MBeanServerConnection connection = getConnection(pid);
			if(connection == null)
				showConnectionErrorAndExit(pid);
	
			Pattern pattern = Pattern.compile(loggerNamePattern);
			Map<String, ObjectName> names = createObjectNames(loggerType);
			for(Iterator<String> it = names.keySet().iterator(); it.hasNext(); /* empty */) {
				String loggerTypeKey = it.next();
				ObjectName name = names.get(loggerTypeKey);
				try {
					String[] loggerNames = (String[])connection.getAttribute(name, "LoggerNames");
					for(String ln : loggerNames) {
						if(pattern.matcher(ln).matches()) {
							String parentName = getParentLoggerName(connection, name, ln);
							System.out.println(
								loggerTypeKey + " " + ln + " > " 
									+ checkLoggerName(parentName)
									+ " : " + getLoggerLevel(connection, name, parentName)
							);
						}
					}
				}
				catch(InstanceNotFoundException e) {
					// Does nothing. Just skips the loop.
				}
			}
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		catch(MalformedObjectNameException e) {
			e.printStackTrace();
		}
		catch(NullPointerException e) {
			e.printStackTrace();
		}
		catch(ReflectionException e) {
			e.printStackTrace();
		}
		catch(AttributeNotFoundException e) {
			e.printStackTrace();
		}
		catch(MBeanException e) {
			e.printStackTrace();
		}
	}
	
	private static void setAndShowLoggerLevel(
		MBeanServerConnection connection,
		ObjectName name,
		String loggerTypeKey,
		String loggerName,
		String level
	)
		throws
			InstanceNotFoundException,
			MBeanException,
			ReflectionException,
			IOException
	{
		try {
			String oldLevel = getLoggerLevel(connection, name, loggerName);
			setLoggerLevel(connection, name, loggerName, level);
			System.out.println(
				loggerTypeKey + " " + checkLoggerName(loggerName) + " : " + oldLevel + " -> " 
					+ getLoggerLevel(connection, name, loggerName)
			);
		}
		catch(RuntimeMBeanException e) {
			System.out.println(
				loggerTypeKey + " " + checkLoggerName(loggerName) + " : Level '"
					+ level + "' is not accepted. Using " 
					+ getLoggerLevel(connection, name, loggerName)
			);
		}
	}
	
	private static void setLevels(
		Integer pid,
		String loggerType,
		String loggerNamePattern,
		String level
	)
	{
		try {
			MBeanServerConnection connection = getConnection(pid);
			if(connection == null)
				showConnectionErrorAndExit(pid);
				
			Pattern pattern = Pattern.compile(loggerNamePattern);
			Map<String, ObjectName> names = createObjectNames(loggerType);
			for(Iterator<String> it = names.keySet().iterator(); it.hasNext(); /* empty */) {
				String loggerTypeKey = it.next();
				ObjectName name = names.get(loggerTypeKey);
				try {
					if(loggerNamePattern.equals("root"))
						setAndShowLoggerLevel(connection, name, loggerTypeKey, "", level);
					else {
						String[] loggerNames =
							(String[])connection.getAttribute(name, "LoggerNames");
						for(String ln : loggerNames) {
							if(pattern.matcher(ln).matches())
								setAndShowLoggerLevel(connection, name, loggerTypeKey, ln, level);
						}
					}
				}
				catch(InstanceNotFoundException e) {
					// Does nothing. Just skips the loop.
				}
			}
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		catch(MalformedObjectNameException e) {
			e.printStackTrace();
		}
		catch(NullPointerException e) {
			e.printStackTrace();
		}
		catch(ReflectionException e) {
			e.printStackTrace();
		}
		catch(AttributeNotFoundException e) {
			e.printStackTrace();
		}
		catch(MBeanException e) {
			e.printStackTrace();
		}
	}

	private static Map<String, ObjectName> createObjectNames(String loggerType)
		throws MalformedObjectNameException
	{
		Map<String, ObjectName> names = new HashMap<String, ObjectName>();
		if(loggerType == null || loggerType.equalsIgnoreCase("j"))
			names.put("(J)", new ObjectName(JAVA_LOGGING_NAME));
		if(loggerType == null || loggerType.equals("4"))
			names.put("(4)", new ObjectName(LOG4J_LOGGING_NAME));
		return names;
	}
	
}
