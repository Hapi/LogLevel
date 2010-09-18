package com.hapiware.jmx;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
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
	private static final String LOG4J_LOGGING_NAME = "org.apache.log4j:type=Logging";

	
	public static void main(String[] args)
	{
		// Checks first the correct number of arguments.
		if(args.length == 0 || args.length > 5)
			usageAndExit(-1);
		if((args[0].equalsIgnoreCase("j") || args[0].equalsIgnoreCase("jobs")) && args.length != 1)
			usageAndExit(-1);
		if(
			(args[0].equalsIgnoreCase("l") || args[0].equalsIgnoreCase("list"))
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

		// Each command is handled separately.
		if(args[0].equalsIgnoreCase("j") || args[0].equalsIgnoreCase("jobs")) {
			listJvms();
		} else
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
			} else
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
				} else {
					System.out.println("Command (" + args[0] + ") not recognized.");
					usageAndExit(-1);
				}
	}
	
	private static void usageAndExit(int status)
	{
		final String logLevel = "java -jar loglevel.jar";
		System.out.println("Description: Lists and sets the logging level of Java Loggers (java.util.logging.Logger) on the run.");
		System.out.println("             Optionally log4j is also supported but it requires java.util.logging.LoggingMXBean");
		System.out.println("             interface to be implemented for log4j. See http://www.hapiware.com/jmx-tools.");
		System.out.println();
		System.out.println("Usage: " + logLevel + " [-? | -h | -help | --help]");
		System.out.println("       " + logLevel + " COMMAND [ARGS]");
		System.out.println();
		System.out.println("       COMMAND:");
		System.out.println("          j");
		System.out.println("          jobs");
		System.out.println("              Shows PIDs and names of running JVMs (jobs). PID starting with");
		System.out.println("              an asterisk (*) means that JMX agent is not runnig on that JVM.");
		System.out.println("              Start the target JVM with -Dcom.sun.management.jmxremote or");
		System.out.println("              if you are running JVM 1.6 or later use startjmx service.");
		System.out.println();
		System.out.println("          l PID [j|J|4] PATTERN");
		System.out.println("          list PID [j|J|4] PATTERN");
		System.out.println("              Lists current logging levels for all loggers matching PATTERN");
		System.out.println("              (Java RE) of a JVM process with PID. Java loggers are identified");
		System.out.println("              by (J) prefix and log4j loggers by (4). After PID argument may");
		System.out.println("              be an optional argument to focus 'list' command only to either");
		System.out.println("              logger type. J or j for Java logger and 4 for log4j logger.");
		System.out.println();
		System.out.println("          s PID [j|J|4] PATTERN LEVEL");
		System.out.println("          set PID [j|J|4] PATTERN LEVEL");
		System.out.println("              Sets a new logging level LEVEL for all loggers matching PATTERN");
		System.out.println("              (Java RE) of a JVM process with PID. Java loggers are identified");
		System.out.println("              by (J) prefix and log4j loggers by (4). After PID argument may");
		System.out.println("              be an optional argument to focus 'set' command only to either");
		System.out.println("              logger type. J or j for Java logger and 4 for log4j logger.");
		System.out.println();
		System.out.println("Examples:");
		System.out.println("    " + logLevel + " -?");
		System.out.println("    " + logLevel + " jobs");
		System.out.println("    " + logLevel + " l 50001 ^.+");
		System.out.println("    " + logLevel + " set 50001 j ^com\\.hapiware\\.Test.* INFO");
		System.out.println();
		System.exit(status);
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
				new String[] { loggerName },
				new String[] { "java.lang.String" }
			);
		if(level == null || level.trim().length() == 0)
			level = "Not defined";
		return level;
	}
	
	private static void showLevels(Integer pid, String loggerType, String loggerNamePattern)
	{
		try {
			MBeanServerConnection connection = getConnection(pid);
			if(connection == null) {
				System.out.println("Cannot connect to " + pid + ". JMX agent is not running.");
				System.out.println(
					"Start the target JVM with -Dcom.sun.management.jmxremote or" 
					+ " if you are running JVM 1.6 or later use startjmx service."
				);
				System.exit(-1);
			}
			Pattern pattern = Pattern.compile(loggerNamePattern);
			Map<String, ObjectName> names = createObjectNames(loggerType);
			for(Iterator<String> it = names.keySet().iterator(); it.hasNext(); /* empty */) {
				String key = it.next();
				ObjectName name = names.get(key);
				try {
					String[] loggerNames = (String[])connection.getAttribute(name, "LoggerNames");
					for(String ln : loggerNames) {
						if(pattern.matcher(ln).matches())
							System.out.println(
								key + " " + ln + " : " + getLoggerLevel(connection, name, ln)
							);
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
	
	private static void setLevels(
		Integer pid,
		String loggerType,
		String loggerNamePattern,
		String level
	)
	{
		try {
			MBeanServerConnection connection = getConnection(pid);
			if(connection == null) {
				System.out.println("Cannot connect to " + pid + ". JMX agent is not running.");
				System.out.println(
					"Start the target JVM with -Dcom.sun.management.jmxremote or" 
					+ " if you are running JVM 1.6 or later use startjmx service."
				);
				System.exit(-1);
			}
			Pattern pattern = Pattern.compile(loggerNamePattern);
			Map<String, ObjectName> names = createObjectNames(loggerType);
			for(Iterator<String> it = names.keySet().iterator(); it.hasNext(); /* empty */) {
				String key = it.next();
				ObjectName name = names.get(key);
				String[] loggerNames = (String[])connection.getAttribute(name, "LoggerNames");
				for(String ln : loggerNames) {
					if(pattern.matcher(ln).matches()) {
						String oldLevel = getLoggerLevel(connection, name, ln);
						connection.invoke(
							name,
							"setLoggerLevel",
							new String[] { ln, level },
							new String[] { "java.lang.String", "java.lang.String" }
						);
						System.out.println(
							key + " " + ln + " : " + oldLevel + " -> " + level
						);
					}
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
		catch(InstanceNotFoundException e) {
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
