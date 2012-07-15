/*
 *
 * Copyright 2011 Performize-IT LTD.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.performizeit.jmxsupport;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class JMXConnection {

    String host;
    String port;
    String userName = "";
    String userPassword = "";
    JMXServiceURL serviceURL;
    private static final String CONNECTOR_ADDRESS =
            "com.sun.management.jmxremote.localConnectorAddress";
    private String connectURL;

    public static void addURL(File file) throws RuntimeException {
        try {
            URL url = file.toURL();
            URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Class clazz = URLClassLoader.class;

            // Use reflection
            Method method = clazz.getDeclaredMethod("addURL", new Class[]{URL.class});
            method.setAccessible(true);
            method.invoke(classLoader, new Object[]{url});

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Class addToolsJar() {
        try {
            return com.sun.tools.attach.VirtualMachine.class;
        } catch (Throwable t) {
            System.out.println("tools.jar not in class path ");
            File toolsJar = new File(System.getProperty("java.home") + "/lib/tools.jar"); //when jdk
            System.out.println("try:" + toolsJar);
            if (toolsJar.exists()) {
                addURL(toolsJar);
                System.out.println(toolsJar);
            } else {
                toolsJar = new File(System.getProperty("java.home") + "/../lib/tools.jar"); // when jre part of jdk
                System.out.println("try:" + toolsJar);
                if (toolsJar.exists()) {
                    addURL(toolsJar);
                    System.out.println(toolsJar);
                } else {
                    System.out.println("Unable to locate tools.jar pls add it to classpath");
                }
            }
        }
        return com.sun.tools.attach.VirtualMachine.class;


    }

    public JMXConnection(String pid) throws AttachNotSupportedException, IOException, AgentLoadException, AgentInitializationException {
        addToolsJar();
        //addURL(new File("conf").toURL());
        connectURL = pid;
        // attach to the target application
        com.sun.tools.attach.VirtualMachine vm =
                com.sun.tools.attach.VirtualMachine.attach(pid.toString());
        JMXServiceURL u;
        try {
            // get the connector address
            String connectorAddress =
                    vm.getAgentProperties().getProperty(CONNECTOR_ADDRESS);

            // no connector address, so we start the JMX agent
            if (connectorAddress == null) {
                String agent = vm.getSystemProperties().getProperty("java.home")
                        + File.separator + "lib" + File.separator
                        + "management-agent.jar";
                vm.loadAgent(agent);

                // agent is started, get the connector address
                connectorAddress =
                        vm.getAgentProperties().getProperty(CONNECTOR_ADDRESS);
            }

            // establish connection to connector server
            // System.out.println(connectorAddress);
            serviceURL = new JMXServiceURL(connectorAddress);

        } finally {
            vm.detach();
        }
    }

    public JMXConnection(String serverUrl, String passwd) throws MalformedURLException {

        int colonIndex = serverUrl.lastIndexOf(":");
        int atIndex = serverUrl.indexOf("@");
        if (atIndex != -1) {
            userName = serverUrl.substring(0, atIndex);
            userPassword = passwd;
        }
        host = serverUrl.substring(atIndex + 1, colonIndex);
        port = serverUrl.substring(colonIndex + 1);
        connectURL = host + ":" + port;
        //    System.out.println("[" + host + "] [" + port + "] [" + userName + "] [" + userPassword + "]");
        serviceURL = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi");
    }
    MBeanServerConnection server = null;

    public String getConnectURL() {
        return connectURL;
    }

    public MBeanServerConnection getServerConnection() throws MalformedURLException, IOException {
        if (server == null) {

            Map env = new HashMap();
            if (userName.length() > 0) {
                String[] creds = {userName, userPassword};
                env.put(JMXConnector.CREDENTIALS, creds);
            }
            JMXConnector conn = JMXConnectorFactory.connect(serviceURL, env);
            server = conn.getMBeanServerConnection();
        }
        return server;
    }
    public static ObjectName RUNTIME = null;
    public static ObjectName GC = null;
    public static ObjectName THREADING = null;

    static {
        try {
            RUNTIME = new ObjectName("java.lang:type=Runtime");
            GC = new ObjectName("java.lang:type=GarbageCollector,name=*");
            THREADING = new ObjectName("java.lang:type=Threading");
        } catch (MalformedObjectNameException ex) {
            Logger.getLogger(JMXConnection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NullPointerException ex) {
            Logger.getLogger(JMXConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public long getUptime() {
        long l = -1;
        try {

            l = (Long) getServerConnection().getAttribute(JMXConnection.RUNTIME, "Uptime");

        } catch (MBeanException ex) {
            Logger.getLogger(JMXConnection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (AttributeNotFoundException ex) {
            Logger.getLogger(JMXConnection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InstanceNotFoundException ex) {
            Logger.getLogger(JMXConnection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ReflectionException ex) {
            Logger.getLogger(JMXConnection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(JMXConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
        return l;
    }

    public static float inSecsTimestamp(long ts) {
        return ((float) ts) / 1000;

    }

    public boolean isUseAuthentication() {
        return !userName.isEmpty();
    }
}
