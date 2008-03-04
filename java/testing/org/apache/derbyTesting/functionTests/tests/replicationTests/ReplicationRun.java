/*
 
Derby - Class org.apache.derbyTesting.functionTests.tests.replicationTests.ReplicationRun
 
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
 
 */
package org.apache.derbyTesting.functionTests.tests.replicationTests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.derby.drda.NetworkServerControl;
import java.net.InetAddress;
import java.util.Properties;

import java.sql.*;
import java.io.*;

import org.apache.derbyTesting.junit.BaseTestCase;

/**
 * Framework to run replication tests.
 * Subclass to create specific tests as 
 * in ReplicationRun_Local and ReplicationRun_Distributed.
 */

public class ReplicationRun extends BaseTestCase
{
    
    /**
     * Name of properties file defining the test environment
     * and replication tests to be run.
     * Located in <CODE>${user.dir}</CODE>
     */
    final static String REPLICATIONTEST_PROPFILE = "replicationtest.properties";
    
    static String testUser = null;
    
    static String userDir = null;
    
    static String masterServerHost = "localhost"; 
    static int masterServerPort = 1527; // .. default..
    static String slaveServerHost = "localhost";
    static int slaveServerPort = 3527; // .. ..
    static String testClientHost = "localhost";
    static int slaveReplPort = 6666;
    
    static String masterDatabasePath = null;
    static String slaveDatabasePath = null;
    static String replicatedDb = "test";
    
    static String bootLoad = ""; // The "test" to run when booting the master database.
    
    static String freezeDB = ""; // Preliminary: need to "manually" freeze db as part of initialization.
    static String unFreezeDB = ""; // Preliminary: need to "manually" unfreeze db as part of initialization.
    
    static boolean runUnReplicated = false;
    
    static int tuplesToInsert = 10000;
    static int commitFreq = 0; // autocommit
    
    static String masterDbSubPath = "db_master";
    static String slaveDbSubPath = "db_slave";
    static final String DB_UID = "";
    static final String DB_PASSWD = "";
    
    
    static String replicationTest = "";
    static String replicationVerify = "";
    
    static String sqlLoadInit = "";
    
    final static String networkServerControl = "org.apache.derby.drda.NetworkServerControl";
    static String specialTestingJar = null;
    // None null if using e.g. your own modified tests.
    static String jvmVersion = null;
    static String masterJvmVersion = null;
    static String slaveJvmVersion = null;
    static String derbyVersion = null;
    static String derbyMasterVersion = null; // Needed for PoC. Remove when committed.
    static String derbySlaveVersion = null;  // Needed for PoC. Remove when committed.
    
    static String junit_jar = null; // Path for JUnit jar
    static String test_jars = null; // Path for derbyTesting.jar:junit_jar
    
    final static String FS = File.separator;
    final static String JVMloc = FS+".."+FS+"bin"+FS+"java"; // "/../bin/java"
    
    static boolean showSysinfo = false;
    static long SLEEP_TIME_MILLIS = 5000L;
    
    static long sleepTime = 5000L; // millisecs.
    
    static final String DRIVER_CLASS_NAME = "org.apache.derby.jdbc.ClientDriver";
    static final String DB_PROTOCOL="jdbc:derby";
    
    static final String ALL_INTERFACES = "0.0.0.0";
    
    static String LF = null;
    
    static final String remoteShell = "/usr/bin/ssh -x"; // or /usr/bin/ssh ?
    
    Utils util = new Utils();
    
    State state = new State();

    static boolean localEnv = false; // true if all hosts have access to
                                             // same filesystem (NFS...)
    
    static String derbyProperties = null;

    NetworkServerControl masterServer;

    NetworkServerControl slaveServer;

    String classPath = null; // Used in "localhost" testing.
    
    /**
     * Creates a new instance of ReplicationRun
     * @param testcaseName Identifying the test.
     */
    public ReplicationRun(String testcaseName)
    {
        super(testcaseName);
        
        LF = System.getProperties().getProperty("line.separator");
    }
    
    /**
     * Parent super()
     * @throws java.lang.Exception .
     */
    protected void setUp() throws Exception
    {
        super.setUp();
    }
    
    /**
     * Parent super()
     * @throws java.lang.Exception .
     */
    protected void tearDown() throws Exception
    {
        super.tearDown();
    }
    
    public static Test suite()
    {
        
        TestSuite suite = new TestSuite("Replication Suite");
        
        suite.addTestSuite( ReplicationRun.class ); // Make sure to rename in subclasses!
        
        return suite;
    }
    
    //////////////////////////////////////////////////////////////
    ////
    //// The replication test framework (testReplication()):
    //// a) "clean" replication run starting master and slave servers,
    ////     preparing master and slave databases,
    ////     starting and stopping replication and doing
    ////     failover for a "normal"/"failure free" replication
    ////     test run.
    //// b)  Running (positive and negative) tests at the various states 
    ////     of replication to test what is and is not accepted compared to
    ////     the functional specification.
    //// c)  Adding additional load on master and slave servers in 
    ////     different states of replication.
    ////
    //////////////////////////////////////////////////////////////
    
    public void testReplication()
    throws Exception
    {
        System.out.println("WARNING: Override in subclass of ReplicationRun. "
                + "See ReplicationRun_Local for an example.");
    }

    void connectPing(String fullDbPath, 
            String serverHost, int serverPort, 
            String testClientHost)
        throws InterruptedException
    {
        
        String serverURL = "jdbc:derby:"
                +"//"+serverHost+":"+serverPort+"/";
        String dbURL = serverURL
                +fullDbPath;
        Connection conn = null;
        boolean done = false;
        int count = 0;
        while ( !done )
        {
            try
            {
                conn = DriverManager.getConnection(dbURL);
                done = true;
                util.DEBUG("Connected");
                conn.close();
            }
            catch ( SQLException se )
            {
                int errCode = se.getErrorCode();
                String msg = se.getMessage();
                String state = se.getSQLState();
                String expectedState = "08004";
                util.DEBUG("startSlave Got SQLException: " + errCode + " " + state + " " + msg);
                if ( (errCode == -1)
                && (state.equalsIgnoreCase(expectedState) ) )
                {
                    util.DEBUG("Failover not complete.");
                    Thread.sleep(200L); // ms.
                }
                else
                {
                    se.printStackTrace(); // FIXME!
                    return;
                }
            }
            
            assertTrue("Failover did not succeed.", count++ < 100); // 100*200ms = 20s.
        }
    }
    
    private void shutdownDb(String serverHost, int serverPort, 
            String dbPath, String replicatedDb)
        throws SQLException
    {
        String serverURL = "jdbc:derby:"
                +"//"+serverHost+":"+serverPort+"/";
        String dbURL = serverURL
                +dbPath
                +"/"+replicatedDb;
        System.out.println("**** DriverManager.getConnection(\"" + dbURL+";shutdown=true\");");

        DriverManager.getConnection(dbURL+";shutdown=true");
        
    }
    
    ////////////////////////////////////////////////////////////////
    /* Utilities.... */
    void startServerMonitor(String slaveHost)
    {
        util.DEBUG("startServerMonitor(" + slaveHost + ") NOT YET IMPLEMENTED.");
    }
    
    void runTest(String replicationTest,
            String clientVM,
            String testClientHost,
            String serverHost, int serverPort,
            String dbName)
            throws Exception
    {
        util.DEBUG("runTest(" + replicationTest
                + ", " + clientVM
                + ", " + testClientHost
                + ", " + serverHost
                + ", " + serverPort
                + ", " + dbName
                + ") "
                );
        
        
        String URL = DB_PROTOCOL
                +"://"+serverHost
                +":"+serverPort
                +FS+masterDatabasePath+FS+masterDbSubPath+FS+dbName;
        String ijClassPath = derbyVersion +FS+ "derbyclient.jar"
                + ":" + derbyVersion +FS+ "derbyTesting.jar"
                + ":" + derbyVersion +FS+ "derbytools.jar";
        String testingClassPath = derbyVersion +FS+ "derbyclient.jar"
                + ":" + derbyVersion +FS+ "derbynet.jar" // WHY IS THIS NEEDED?
                // See TestConfiguration: startNetworkServer and stopNetworkServer
                + ":" + test_jars;
        
        String clientJvm = clientVM+JVMloc;
        
        String command = null;
        
        if ( replicationTest == null ) 
        {
            util.DEBUG("No replicationTest specified. Exitting.");
            return;
        } 
        
        util.DEBUG("replicationTest: " + replicationTest);
        if ( replicationTest.indexOf(".sql") >= 0 )
        {
            command = clientJvm
                    + " -Dij.driver=" + DRIVER_CLASS_NAME
                    + " -Dij.connection.startTestClient=" + URL
                    + " -classpath " + ijClassPath + " org.apache.derby.tools.ij"
                    + " " + replicationTest
                    ;
        }
        else
        { // JUnit
            if ( classPath != null ) // "localhost" case...
            {
                testingClassPath = classPath; // Using the complete classpath
            }
            command = clientJvm
                    + " -Dderby.tests.trace=true"
                    + " -Djava.security.policy=\"<NONE>\"" // To allow the test to e.g. kill servers...
                    + " -Dtest.serverHost=" + serverHost  // Tell the test what server
                    + " -Dtest.serverPort=" + serverPort  // and port to connect to.
                    + " -Dtest.inserts=" + tuplesToInsert // for SimplePerfTest
                    + " -Dtest.commitFreq=" +  commitFreq // for SimplePerfTest
                    + " -classpath " + testingClassPath
                    + " junit.textui.TestRunner"
                    + " " + replicationTest
                    ;
        }
        
        long startTime = System.currentTimeMillis();
        String results = null;
        if ( testClientHost.equalsIgnoreCase("localhost") )
        {
            runUserCommandLocally(command, userDir, "runTest ");
        }
        else
        {
            command = "cd "+ userDir +";" // Must be positioned where the properties file is located.
                    + command;
            results = runUserCommandRemotely(command, testClientHost, testUser,
                    "runTest ");
        }
        System.out.println("Time: " + (System.currentTimeMillis() - startTime) / 1000.0);
        
    }
    void runTestOnSlave(String replicationTest,
            String clientVM,
            String testClientHost,
            String serverHost, int serverPort,
            String dbName)
            throws Exception
    {
        util.DEBUG("runTest(" + replicationTest
                + ", " + clientVM
                + ", " + testClientHost
                + ", " + serverHost
                + ", " + serverPort
                + ", " + dbName
                + ") "
                );
        
        
        String URL = DB_PROTOCOL
                +"://"+serverHost
                +":"+serverPort
                +FS+slaveDatabasePath+FS+slaveDbSubPath+FS+dbName;
        String ijClassPath = derbyVersion +FS+ "derbyclient.jar"
                + ":" + derbyVersion +FS+ "derbyTesting.jar"
                + ":" + derbyVersion +FS+ "derbytools.jar";
        String testingClassPath = derbyVersion +FS+ "derbyclient.jar"
                + ":" + derbyVersion +FS+ "derbynet.jar" // WHY IS THIS NEEDED?
                // See TestConfiguration: startNetworkServer and stopNetworkServer
                + ":" + test_jars;
        
        String clientJvm = clientVM+JVMloc;
        
        String command = null;
        
        if ( replicationTest == null ) 
        {
            util.DEBUG("No replicationTest specified. Exitting.");
            return;
        } 
        
        util.DEBUG("replicationTest: " + replicationTest);
        if ( replicationTest.indexOf(".sql") >= 0 )
        {
            command = clientJvm
                    + " -Dij.driver=" + DRIVER_CLASS_NAME
                    + " -Dij.connection.startTestClient=" + URL
                    + " -classpath " + ijClassPath + " org.apache.derby.tools.ij"
                    + " " + replicationTest
                    ;
        }
        else
        { // JUnit
            if ( classPath != null ) // "localhost" case...
            {
                testingClassPath = classPath; // Using the complete classpath
            }
            command = clientJvm
                    + " -Dderby.tests.trace=true"
                    + " -Djava.security.policy=\"<NONE>\"" // To allow the test to e.g. kill servers...
                    + " -Dtest.serverHost=" + serverHost  // Tell the test what server
                    + " -Dtest.serverPort=" + serverPort  // and port to connect to.
                    + " -Dtest.inserts=" + tuplesToInsert // for SimplePerfTest
                    + " -Dtest.commitFreq=" +  commitFreq // for SimplePerfTest
                    + " -classpath " + testingClassPath
                    + " junit.textui.TestRunner"
                    + " " + replicationTest
                    ;
        }
        
        long startTime = System.currentTimeMillis();
        String results = null;
        if ( testClientHost.equalsIgnoreCase("localhost") )
        {
            runUserCommandLocally(command, userDir, "runTest ");
        }
        else
        {
            command = "cd "+ userDir +";" // Must be positioned where the properties file is located.
                    + command;
            results = runUserCommandRemotely(command, testClientHost, testUser,
                    "runTest ");
        }
        System.out.println("Time: " + (System.currentTimeMillis() - startTime) / 1000.0);
        
    }
    
    /*
     *
     * Should allow:
     * - Run load in separate thread.
     *
     */
    private void runLoad(String load,
            String clientVM,
            String testClientHost,
            String masterHost, int masterPort,
            String dbSubPath) // FIXME? Should we allow extra URL options?
            throws Exception
    {
        util.DEBUG("runLoad(" + load
                + ", " + clientVM
                + ", " + testClientHost
                + ", " + masterHost
                + ", " + masterPort
                + ", " + dbSubPath
                + ") "
                );
        
        
        String URL = DB_PROTOCOL
                +"://"+masterHost
                +":"+masterPort
                +FS+masterDatabasePath+ /* FS+masterDbSubPath+ */ FS+dbSubPath;
        String ijClassPath = derbyVersion +FS+ "derbyclient.jar"
                + ":" + derbyVersion +FS+ "derbyTesting.jar"
                // Needed for 'run resource 'createTestProcedures.subsql';' cases?
                // Nope? what is 'resource'?
                + ":" + derbyVersion +FS+ "derbytools.jar";
        String testingClassPath = derbyVersion +FS+ "derbyclient.jar"
                + ":" + derbyVersion +FS+ "derbynet.jar" // WHY IS THIS NEEDED?
                // See TestConfiguration: startNetworkServer and stopNetworkServer
                + ":" + test_jars;
        
        String clientJvm = clientVM+JVMloc;
        
        String command = null;
        
        util.DEBUG("load: " + load);
        if ( load.indexOf(".sql") >= 0 )
        {
            command = clientJvm // "java"
                    + " -Dij.driver=" + DRIVER_CLASS_NAME
                    + " -Dij.connection.startTestClient=" + URL
                    + " -classpath " + ijClassPath + " org.apache.derby.tools.ij"
                    + " " + load
                    ;
        }
        else
        {
            /* BEGIN For junit: */
            command = clientJvm // "java"
                    + " -Dderby.tests.trace=true"
                    + " -Djava.security.policy=\"<NONE>\"" // To allow the test to e.g. kill servers...
                    + " -classpath " + testingClassPath
                    + " junit.textui.TestRunner"
                    + " " + load //
                    // + " org.apache.derbyTesting.functionTests.tests.replicationTests.ReplicationTestRun"
                    ;
            /* END */
        }
        
        /* String results = */
        if ( testClientHost.equalsIgnoreCase("localhost") )
        {
            runUserCommandInThread(command,  testUser, dbSubPath,
                    "runLoad["+dbSubPath+"] ");
        }
        else
        {
            runUserCommandInThreadRemotely(command,
                    testClientHost, testUser, dbSubPath,
                    "runLoad["+dbSubPath+"] ");
        }
        
    }
    
    private void runStateTest(String stateTest,
            String clientVM,
            String testClientHost,
            String masterHost, int masterPort, // serverHost?, serverPort?
            String dbSubPath) // FIXME? Should we allow extra URL options?
            throws Exception
    {
        util.DEBUG("runStateTest(" + stateTest
                + ", " + clientVM
                + ", " + testClientHost
                + ", " + masterHost
                + ", " + masterPort
                + ", " + dbSubPath
                + ") "
                );
        
        
        String URL = DB_PROTOCOL
                +"://"+masterHost
                +":"+masterPort
                +FS+masterDatabasePath+ /* FS+masterDbSubPath+ */ FS+dbSubPath;
        String ijClassPath = derbyVersion +FS+ "derbyclient.jar"
                + ":" + derbyVersion +FS+ "derbyTesting.jar"
                // Needed for 'run resource 'createTestProcedures.subsql';' cases?
                // Nope? what is 'resource'?
                + ":" + derbyVersion +FS+ "derbytools.jar";
        String testingClassPath = derbyVersion +FS+ "derbyclient.jar"
                + ":" + derbyVersion +FS+ "derbynet.jar" // WHY IS THIS NEEDED?
                // See TestConfiguration: startNetworkServer and stopNetworkServer
                + ":" + test_jars;
        
        String clientJvm = clientVM+JVMloc;
        
        String command = null;
        
        util.DEBUG("stateTest: " + stateTest);
        if ( stateTest.indexOf(".sql") >= 0 )
        {
            command = clientJvm 
                    + " -Dij.driver=" + DRIVER_CLASS_NAME
                    + " -Dij.connection.startTestClient=" + URL
                    + " -classpath " + ijClassPath + " org.apache.derby.tools.ij"
                    + " " + stateTest
                    ;
        }
        else
        {
            /* BEGIN For junit: */
            command = "cd "+ userDir +";" // Must be positioned where the properties file is located.
                    + clientJvm 
                    + " -Dderby.tests.trace=true"
                    + " -Djava.security.policy=\"<NONE>\"" // To allow the test to e.g. kill servers...
                    + " -classpath " + testingClassPath
                    + " junit.textui.TestRunner"
                    + " " + stateTest
                    ;
            /* END */
        }
        
        /* String results = */
        runUserCommandRemotely(command,
                testClientHost, // masterHost,
                testUser,
                // dbSubPath,
                "runStateTest "); // ["+dbSubPath+"]
        
    }
    
    void bootMasterDatabase(String clientVM, 
            String dbSubPath,
            String dbName,
            String masterHost,  // Where the command is to be executed.
            int masterServerPort, // master server interface accepting client requests
            String load)
        throws Exception
    {
        // Should just do a "connect....;create=true" here, instead of copying in initMaster.
        
        String URL = DB_PROTOCOL
                +"://"+masterHost
                +":"+masterServerPort
                +FS+masterDatabasePath+FS+masterDbSubPath+FS+dbName
                +";create=true";

        String ijClassPath = derbyVersion +FS+ "derbyclient.jar"
                + ":" + derbyVersion +FS+ "derbytools.jar";
        
        String clientJvm = jvmVersion+JVMloc;
        
        String command = clientJvm 
                + " -Dij.driver=" + DRIVER_CLASS_NAME
                + " -Dij.connection.createMaster=\"" + URL + "\""
                + " -classpath " + ijClassPath + " org.apache.derby.tools.ij"
                + " " + "/home/os136789/Replication/testing/exit.sql" // FIXME!!
                ;
        
        String results = null;
        if ( masterHost.equalsIgnoreCase("localhost") || localEnv )
        {
            /* results = 
                runUserCommand(command,
                testUser,
                "bootMasterDatabase "); */
            util.DEBUG("bootMasterDatabase getConnection("+URL+")");
            Connection conn = DriverManager.getConnection(URL);
            conn.close();
        }
        else
        {
        // Execute the ij command on the testClientHost as testUser
            results =
                runUserCommandRemotely(command,
                testClientHost,
                testUser,
                "bootMasterDatabase ");
        }
        System.out.println(results);
        
        
        // NB! should be done by startMaster. Preliminary needs to freeze db before copying to slave and setting replication mode.
        util.DEBUG("************************** DERBY-???? Preliminary needs to freeze db before copying to slave and setting replication mode.");
        if ( masterServerHost.equalsIgnoreCase("localhost") || localEnv )
        {
             URL = DB_PROTOCOL
                +"://"+masterServerHost
                +":"+masterServerPort
                +FS+masterDatabasePath+FS+masterDbSubPath+FS+dbName;
            util.DEBUG("bootMasterDatabase getConnection("+URL+")");
            Connection conn = DriverManager.getConnection(URL);
            Statement s = conn.createStatement();
            s.execute("call syscs_util.syscs_freeze_database()");
            conn.close();
        }
        else
        {
        runTest(freezeDB,
                clientVM,
                testClientHost,
                masterServerHost, masterServerPort,
                dbName
                );
        }
        
        if ( load != null )
        {
            runLoad(load,
                    clientVM, // jvmVersion,
                    testClientHost,
                    masterServerHost, masterServerPort,
                    dbSubPath+FS+dbName);
        }
    }

    /**
     * Set master db in replication master mode.
     */
    void startMaster(String clientVM,
            String dbName,
            String masterHost,  // Where the command is to be executed.
            int masterServerPort, // master server interface accepting client requests
            String slaveClientInterface, // Will be = slaveReplInterface = slaveHost if only one interface card used.
            int slaveServerPort, // masterPort, // Not used since slave don't accept client requests
            String slaveReplInterface, // slaveHost,
            int slaveReplPort) // slavePort)
            throws Exception
    {
        if ( masterHost.equalsIgnoreCase("localhost") )
        {
            startMaster_direct(dbName,
                    masterHost,  masterServerPort,
                    slaveReplInterface, slaveReplPort);
        }
        else
        {
            startMaster_ij(jvmVersion,
                    dbName,
                    masterHost, masterServerPort,
                    slaveReplInterface, slaveReplPort,
                    testClientHost);
        }
        /* else if ...
        {
            startMaster_CLI(clientVM,
                    dbName,
                    masterHost,masterServerPort,
                    slaveClientInterface,slaveServerPort,
                    slaveReplInterface,slaveReplPort);
        } */
    }
    /* CLI not available for 10.4 */
    private void startMaster_CLI(String clientVM,
            String dbName,
            String masterHost,  // Where the command is to be executed.
            int masterServerPort, // master server interface accepting client requests
            String slaveClientInterface, // Will be = slaveReplInterface = slaveHost if only one interface card used.
            int slaveServerPort, // masterPort, // Not used since slave don't accept client requests
            String slaveReplInterface, // slaveHost,
            int slaveReplPort) // slavePort)
            throws Exception
    {
        
        String masterClassPath = derbyMasterVersion +FS+ "derbynet.jar";
        
        String clientJvm = clientVM+JVMloc;
        
        /* java -classpath ${MASTER_LIB}/derbynet.jar \
         *       org.apache.derby.drda.NetworkServerControl startreplication test \
         *       -slavehost ${SLAVEREPLINTERFACE} -slaveport ${SLAVEREPLPORT} \
         *       -h ${SLAVECLIENTINTERFACE} -p ${SLAVESERVERPORT}?? \
         *       -noSecurityManager
         */
        String command = clientJvm
                + " -classpath " + masterClassPath
                + " " + networkServerControl
                + " startreplication" // startmaster!
                + " " + dbName
                + " -slavehost " + /*slaveHost*/ slaveReplInterface + " -slaveport " + /*slavePort*/ slaveReplPort
                + " -h " + /*masterHost*/ slaveClientInterface + " -p " + masterServerPort /*masterPort*/ /* see comment above slaveServerPort */
                + " -noSecurityManager"
                ;
        
        util.DEBUG("Executing '" + command + "' on " + masterHost);
        
        // Do rsh/ssh to masterHost and execute the command there.
        
        // runUserCommandRemotely(command, // FIXME?! Should NOT be in sep. thread? Wait for it to complete!?
        runUserCommandInThreadRemotely(command, //
                masterHost,
                testUser,
                masterDbSubPath+FS+dbName,
                "startMaster_CLI ");
        
    }
    private void startMaster_ij(String jvmVersion,
            String dbName,
            String masterHost,  // Where the master db is run.
            int masterServerPort, // master server interface accepting client requests
            
            String slaveReplInterface, // slaveHost,
            int slaveReplPort, // slavePort)
            
            String testClientHost)
            throws Exception
    {
        
        String masterClassPath = derbyMasterVersion +FS+ "derbynet.jar";
                
        String URL = DB_PROTOCOL
                +"://"+masterHost
                +":"+masterServerPort
                +FS+masterDatabasePath+FS+masterDbSubPath+FS+dbName
                +";startMaster=true;slaveHost="+slaveReplInterface
                +";slavePort="+slaveReplPort;
        String ijClassPath = derbyVersion +FS+ "derbyclient.jar"
                + ":" + derbyVersion +FS+ "derbytools.jar";
        
        String clientJvm = jvmVersion+JVMloc;
        
        String command = clientJvm // "java"
                + " -Dij.driver=" + DRIVER_CLASS_NAME
                + " -Dij.connection.startMaster=\"" + URL + "\""
                + " -classpath " + ijClassPath + " org.apache.derby.tools.ij"
                + " " + "/home/os136789/Replication/testing/exit.sql"
                ;
        
        // Execute the ij command on the testClientHost as testUser
        String results =
                runUserCommandRemotely(command,
                testClientHost,
                testUser,
                "startMaster_ij ");
        System.out.println(results);
    }
    private void startMaster_direct(String dbName,
            String masterHost,  // Where the master db is run.
            int masterServerPort, // master server interface accepting client requests
            
            String slaveReplInterface, // slaveHost,
            int slaveReplPort)
            throws Exception
    {
        
        String URL = DB_PROTOCOL
                +"://"+masterHost
                +":"+masterServerPort
                +FS+masterDatabasePath+FS+masterDbSubPath+FS+dbName
                +";startMaster=true;slaveHost="+slaveReplInterface
                +";slavePort="+slaveReplPort;
                
            util.DEBUG("startMaster_direct getConnection("+URL+")");
            Connection conn = null;
            boolean done = false;
            int count = 0;
            while ( !done )
            {
                try
                {
                    conn = DriverManager.getConnection(URL);
                    done = true;
                    conn.close();
                }
                catch ( SQLException se )
                {
                    int errCode = se.getErrorCode();
                    String msg = se.getMessage();
                    String state = se.getSQLState();
                    String expectedState = "XRE04";
                    util.DEBUG("startSlave Got SQLException: " + errCode + " " + state + " " + msg);
                    if ( (errCode == -1)
                    && (state.equalsIgnoreCase(expectedState) ) )
                    {
                        util.DEBUG("Not ready to startMaster");
                        Thread.sleep(100L); // ms.
                    }
                    else
                    {
                        se.printStackTrace(); // FIXME!
                        return;
                    }
                }

                this.assertTrue("startMaster did not succeed.", count++ < 100); // 100*100ms = 10s.
            }
    }
    
    /**
     * Set slave db in replication slave mode
     */
    void startSlave(String clientVM,
            String dbName,
            String slaveClientInterface, // slaveHost, // Where the command is to be executed.
            int slaveServerPort,
            String slaveReplInterface,
            int slaveReplPort,
            String testClientHost)
    throws Exception
    {
        if ( testClientHost.equalsIgnoreCase("localhost") )
        {
            startSlave_direct(dbName,
                    slaveClientInterface, slaveServerPort,
                    slaveReplInterface,slaveReplPort);
        }
        else
        {
            startSlave_ij(jvmVersion,
                    dbName,
                    slaveClientInterface, slaveServerPort,
                    slaveReplInterface, slaveReplPort,
                    testClientHost);
        }
        /* else if ... 
        {
            startSlave_CLI(clientVM,
                    dbName,
                    slaveClientInterface,slaveServerPort,
                    slaveReplInterface,slaveReplPort);
        } */
        
    }
    /* CLI Not available in 10.4 */
    private void startSlave_CLI(String clientVM,
            String dbName,
            String slaveClientInterface, // slaveHost, // Where the command is to be executed.
            int slaveServerPort,
            String slaveReplInterface,
            int slaveReplPort)
            throws InterruptedException
    {
        
        String slaveClassPath = derbySlaveVersion +FS+ "derbynet.jar";
        
        String clientJvm = clientVM+JVMloc;
        
        /*
         * java -classpath ${SLAVE_LIB}/derbynet.jar org.apache.derby.drda.NetworkServerControl \
         *       startslave test -slavehost ${SLAVEREPLINTERFACE}  -slaveport ${SLAVEREPLPORT} \
         *       -h ${SLAVECLIENTINTERFACE} -p ${SLAVESERVERPORT}??  \
         *       -noSecurityManager
         */
        String command = clientJvm
                + " -classpath " + slaveClassPath
                + " " + networkServerControl
                + " startslave"
                + " " + dbName
                + " -slavehost " + slaveReplInterface + " -slaveport " + slaveReplPort
                + " -h " + slaveClientInterface + " -p " + slaveServerPort
                + " -noSecurityManager"
                ;
        
        util.DEBUG("Executing  '" + command + "' on " + slaveClientInterface); // slaveHost
        
        runUserCommandInThreadRemotely(command,
                slaveClientInterface, // slaveHost,
                testUser,
                slaveDbSubPath+FS+dbName,
                "startSlave_CLI ");
        
    }
    private void startSlave_ij(String jvmVersion,
            String dbName,
            String slaveHost,  // Where the slave db is run.
            int slaveServerPort, // slave server interface accepting client requests
            
            String slaveReplInterface, // slaveHost,
            int slaveReplPort, // slavePort)
            
            String testClientHost)
            throws Exception
    {
        
        String masterClassPath = derbyMasterVersion +FS+ "derbynet.jar";
                
        String URL = DB_PROTOCOL
                +"://"+slaveHost
                +":"+slaveServerPort
                +FS+slaveDatabasePath+FS+slaveDbSubPath+FS+dbName
                +";startSlave=true;slaveHost="+slaveReplInterface
                +";slavePort="+slaveReplPort;
        String ijClassPath = derbyVersion +FS+ "derbyclient.jar"
                + ":" + derbyVersion +FS+ "derbytools.jar";
        
        String clientJvm = jvmVersion+JVMloc;
        
        String command = clientJvm // "java"
                + " -Dij.driver=" + DRIVER_CLASS_NAME
                + " -Dij.connection.startSlave=\"" + URL + "\""
                + " -classpath " + ijClassPath + " org.apache.derby.tools.ij"
                + " " + "/home/os136789/Replication/testing/exit.sql"
                ;
        
        // Execute the ij command on the testClientHost as testUser
        /* String results =
                runUserCommandRemotely(command,
                testClientHost,
                testUser,
                "startSlave_ij ");
        System.out.println(results); */
        // 618220 + failover_impl_3205_v3.diff + derby-3361-1a.diff :
        runUserCommandInThreadRemotely(command,
                testClientHost,
                testUser,
                slaveDbSubPath+FS+dbName,
                "startSlave_ij ");
        
    }
    private void startSlave_direct(String dbName,
            String slaveHost,  // Where the slave db is run.
            int slaveServerPort, // slave server interface accepting client requests
            
            String slaveReplInterface,
            int slaveReplPort)
            throws Exception
    {
        final String URL = DB_PROTOCOL
                +"://"+slaveHost
                +":"+slaveServerPort
                +FS+slaveDatabasePath+FS+slaveDbSubPath+FS+dbName
                +";startSlave=true;slaveHost="+slaveReplInterface
                +";slavePort="+slaveReplPort;
        
            util.DEBUG("startSlave_direct getConnection("+URL+")");
            
            Thread connThread = new Thread(
                    new Runnable()
            {
                public void run()
                {
                    Connection conn = null;
                    try {
                        // NB! WIll hang here until startMaster is executed!
                        conn = DriverManager.getConnection(URL);
                        // conn.close();
                    }
                    catch (SQLException se)
                    {
                        int errCode = se.getErrorCode();
                        String msg = se.getMessage();
                        String state = se.getSQLState();
                        String expectedState = "XRE08";
                        System.out.println("startSlave Got SQLException: " + errCode + " " + state + " " + msg);
                        if ( (errCode == -1)
                        && (state.equalsIgnoreCase(expectedState) ) )
                        {
                            System.out.println("As expected.");

                        }
                        else
                        {
                            se.printStackTrace(); // FIXME!
                        }
                        ;
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace(); // FIXME!
                    }
                }
            }
            );
            connThread.start();
    }
    
    private void stopSlave(String dbName)
    {
        util.DEBUG("Simulating '... stopslave -db "+dbName);
    }
    private void stopSlave_ij(String jvmVersion,
            String dbName,
            String slaveHost,  // Where the slave db is run.
            int slaveServerPort,
            
            String testClientHost)
            throws Exception
    {
        
        String masterClassPath = derbyMasterVersion +FS+ "derbynet.jar";
                
        String URL = DB_PROTOCOL
                +"://"+slaveHost
                +":"+slaveServerPort
                +FS+slaveDatabasePath+FS+slaveDbSubPath+FS+dbName
                +";stopSlave=true";
        String ijClassPath = derbyVersion +FS+ "derbyclient.jar"
                + ":" + derbyVersion +FS+ "derbytools.jar";
        
        String clientJvm = jvmVersion+JVMloc;
        
        String command = clientJvm // "java"
                + " -Dij.driver=" + DRIVER_CLASS_NAME
                + " -Dij.connection.stopSlave=\"" + URL + "\""
                + " -classpath " + ijClassPath + " org.apache.derby.tools.ij"
                + " " + "/home/os136789/Replication/testing/exit.sql"
                ;
        
        // Execute the ij command on the testClientHost as testUser
        String results =
                runUserCommandRemotely(command,
                testClientHost,
                testUser,
                "stopSlave_ij ");
        System.out.println(results);
    }
    
    void failOver(String jvmVersion,
            String dbPath, String dbSubPath, String dbName,
            String host,  // Where the db is run.
            int serverPort,
            
            String testClientHost)
            throws Exception
    {
        if ( host.equalsIgnoreCase("localhost") )
        {
            failOver_direct(dbPath, dbSubPath, dbName, host, serverPort);
        }
        else
        {
            failOver_ij(jvmVersion, dbPath, dbSubPath, dbName, host, serverPort,
                    testClientHost);
        }

    }
    private void failOver_ij(String jvmVersion,
            String dbPath, String dbSubPath, String dbName,
            String host,  // Where the db is run.
            int serverPort,
            
            String testClientHost)
            throws Exception
    {
        
        String masterClassPath = derbyMasterVersion +FS+ "derbynet.jar";
                
        String URL = DB_PROTOCOL
                +"://"+host
                +":"+serverPort
                +FS+dbPath+FS+dbSubPath+FS+dbName
                +";failover=true";
        String ijClassPath = derbyVersion +FS+ "derbyclient.jar"
                + ":" + derbyVersion +FS+ "derbytools.jar";
        
        String clientJvm = jvmVersion+JVMloc;
        
        String command = clientJvm // "java"
                + " -Dij.driver=" + DRIVER_CLASS_NAME
                + " -Dij.connection.failover=\"" + URL + "\""
                + " -classpath " + ijClassPath + " org.apache.derby.tools.ij"
                + " " + "/home/os136789/Replication/testing/exit.sql"
                ;
        
        // Execute the ij command on the testClientHost as testUser
        String results =
                runUserCommandRemotely(command,
                testClientHost,
                testUser,
                "failOver_ij ");
        System.out.println(results);
    }
    private void failOver_direct(String dbPath, String dbSubPath, String dbName,
            String host,  // Where the db is run.
            int serverPort)
            throws Exception
    {
        String URL = DB_PROTOCOL
                +"://"+host
                +":"+serverPort
                +FS+dbPath+FS+dbSubPath+FS+dbName
                +";failover=true";
               
            util.DEBUG("failOver_direct getConnection("+URL+")");

            Connection conn = null;
            try
            {
                conn = DriverManager.getConnection(URL);
                // conn.close();
            }
            catch (SQLException se)
            {
                int errCode = se.getErrorCode();
                String msg = se.getMessage();
                String state = se.getSQLState();
                String expectedState = "XRE20";
                System.out.println("failOver_direct Got SQLException: " + errCode + " " + state + " " + msg);
                if ( (errCode == -1)
                && (state.equalsIgnoreCase(expectedState) ) )
                {
                    System.out.println("As expected.");
                    
                }
                else
                {
                    se.printStackTrace(); // FIXME!
                }
                ;
            }
            catch (Exception ex)
            {
                ex.printStackTrace(); // FIXME!
            }
   }
    
    private void stopMaster(String dbName)
    {
        util.DEBUG("Simulating '... stopreplication/stopmaster -db "+dbName
                + " NB! Doing nothing now!");
    }
    private void stopMaster_ij(String jvmVersion,
            String dbName,
            String masterHost,  // Where the master db is run.
            int masterServerPort,
            
            String testClientHost)
            throws Exception
    {
        
        String masterClassPath = derbyMasterVersion +FS+ "derbynet.jar";
                
        String URL = DB_PROTOCOL
                +"://"+masterHost
                +":"+masterServerPort
                +FS+masterDatabasePath+FS+masterDbSubPath+FS+dbName
                +";stopSlave=true";
        String ijClassPath = derbyVersion +FS+ "derbyclient.jar"
                + ":" + derbyVersion +FS+ "derbytools.jar";
        
        String clientJvm = jvmVersion+JVMloc;
        
        String command = clientJvm // "java"
                + " -Dij.driver=" + DRIVER_CLASS_NAME
                + " -Dij.connection.stopMaster=\"" + URL + "\""
                + " -classpath " + ijClassPath + " org.apache.derby.tools.ij"
                + " " + "/home/os136789/Replication/testing/exit.sql"
                ;
        
        // Execute the ij command on the testClientHost as testUser
        String results =
                runUserCommandRemotely(command,
                testClientHost,
                testUser,
                "stopSlave_ij ");
        System.out.println(results);
    }
    
    int xFindServerPID(String serverHost, int serverPort)
    throws InterruptedException
    {
        if ( masterServer != null ) // If master (and assuming then also slave)
            // is started via new NetworkServerContol() use 0 for "PID".
        {
            return 0;
        }
        if ( serverHost.equalsIgnoreCase("localhost") ) 
        { // Assuming we do not need the PID.
            return 0;
        }
        int pid = -1;
        /* String command = "ps auxwww "
                + " | grep " + serverPort
                + " | grep '.NetworkServerControl start -h '"
                // + " | grep '/trunk_slave/jars/'" or trunk_master...
                + " | grep -v grep"
                + " | grep -v ssh"
                + " | gawk '{ print $2 }'"; */
        String p1 = "ps auxwww"; // "/bin/ps auxwww";
        String p2 = " | grep " + serverPort; // /bin/grep
        String p3 = " | grep '.NetworkServerControl start -h '"; // /bin/grep
        String p4 = ""; // | /bin/grep '/trunk_slave/jars/'"; // Also used for master...
        String p5 = " | grep -v grep"; // /bin/grep
        String p6 = " | grep -v ssh"; // /bin/grep
        String p7 = " | grep -v tcsh";  // /bin/grep// Assuming always doing remote command (ssh)
        String p8 = " | gawk '{ print $2 }'"; // /bin/gawk
        String p9 = " | head -1"; // For cases where we also get some error...
        
        String command = p1 + p2 + p3 + p4 + p5 + p6 + p7 /* + p8 */ + ";";
        String result = runUserCommandRemotely(command,serverHost, testUser);
        util.DEBUG("xFindServerPID: '" + result + "'");
        // result = result.split(" ")[1]; // Without ' + p8 ' should show full line. But fails with PID less than 10000!
        command = p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9 + ";";
        result = runUserCommandRemotely(command, serverHost, testUser);
        if ( result == null )
        {util.DEBUG("xFindServerPID: Server process not found");return -1;} // Avoid error on parseInt below
        util.DEBUG("xFindServerPID: '" + result + "'");
        pid = Integer.parseInt(result.trim());
        util.DEBUG("xFindServerPID: " + pid);
        return pid;
    }
    private void xStopServer(String serverHost, int serverPID)
    throws InterruptedException
    {
        if ( serverPID == -1 || serverPID == 0 )
        {util.DEBUG("Illegal PID");return;}
        String command = "kill " + serverPID;
        runUserCommandRemotely(command,
                serverHost,
                testUser,
                "xStopServer");
    }
    
    void verifySlave()
    throws Exception
    {
        util.DEBUG("BEGIN verifySlave PROTOTYPE VARIANT!");
        
        runSlaveVerificationCLient(jvmVersion,
                testClientHost,
                replicatedDb,
                slaveServerHost, slaveServerPort);
        
        util.DEBUG("END   verifySlave PROTOTYPE VARIANT!");
    }
    void verifyMaster()
    throws Exception
    {
        util.DEBUG("BEGIN verifyMaster PROTOTYPE VARIANT!");
        
        runMasterVerificationCLient(jvmVersion,
                testClientHost,
                replicatedDb,
                masterServerHost, masterServerPort);
        
        util.DEBUG("END   verifyMaster PROTOTYPE VARIANT!");
    }
    
    private void runSlaveVerificationCLient(String jvmVersion,
            String testClientHost,
            String dbName,
            String serverHost,
            int serverPort)
            throws Exception
    {
        util.DEBUG("runSlaveVerificationCLient");
        
        runTestOnSlave(replicationVerify,
                jvmVersion,
                testClientHost,
                serverHost,serverPort,
                dbName);
        /*
        String URL = DB_PROTOCOL
                +"://"+serverHost
                +":"+serverPort
                +FS+slaveDatabasePath+FS+slaveDbSubPath+FS+dbName;
        String ijClassPath = derbyVersion +FS+ "derbyclient.jar"
                + ":" + derbyVersion +FS+ "derbytools.jar";
        
        String clientJvm = jvmVersion+JVMloc;
        
        String command = clientJvm // "java"
                + " -Dij.driver=" + DRIVER_CLASS_NAME
                + " -Dij.connection.slave=" + URL
                + " -classpath " + ijClassPath + " org.apache.derby.tools.ij"
                + " " + replicationVerify // FIXME! handle junit verificationclients too!
                ;
        
        String results = null;
        if ( serverHost.equalsIgnoreCase("localhost") )
        {
            runUserCommandLocally(command,
                    userDir,
                    "runSlaveVerificationCLient ");
        }
        else
        {
            results = runUserCommandRemotely(command,
                    testClientHost,
                    testUser,
                    // dbName,
                    "runSlaveVerificationCLient ");
        }
        System.out.println(results);
       */
    }
    
    private void runMasterVerificationCLient(String jvmVersion,
            String testClientHost,
            String dbName,
            String serverHost,
            int serverPort)
            throws Exception
    {
        util.DEBUG("runMasterVerificationCLient");
        
        runTest(replicationVerify,
                jvmVersion,
                testClientHost,
                serverHost,serverPort,
                dbName);
        /*
        String URL = DB_PROTOCOL
                +"://"+serverHost
                +":"+serverPort
                +FS+masterDatabasePath+FS+masterDbSubPath+FS+dbName;
        String ijClassPath = derbyVersion +FS+ "derbyclient.jar"
                + ":" + derbyVersion +FS+ "derbytools.jar";
        
        String clientJvm = jvmVersion+JVMloc;
        
        String command = clientJvm // "java"
                + " -Dij.driver=" + DRIVER_CLASS_NAME
                + " -Dij.connection.master=" + URL
                + " -classpath " + ijClassPath + " org.apache.derby.tools.ij"
                + " " + replicationVerify // FIXME! handle junit verificationclients too!
                ;
        
        String results = null;
        if ( serverHost.equalsIgnoreCase("localhost") )
        {
            runUserCommandLocally(command,
                    userDir,
                    "runMasterVerificationCLient ");            
        }
        else
        {
            results =
                    runUserCommandRemotely(command,
                    testClientHost,
                    testUser,
                    // dbName,
                    "runMasterVerificationCLient ");
        }
        System.out.println(results);
       */
    }
    
    /* UNUSED!
    private static Connection getConnection(String driverClassName,String url,String dbUid,String dbPasswd)
    {
        DEBUG("getConnection: '"+driverClassName+"' '"+url+"' '"+dbUid+"' '"+dbPasswd+"'");
        Connection con = null;
        try
        {
            Class.forName(driverClassName).newInstance();
            con = DriverManager.getConnection(url); //,dbUid,dbPasswd);
        }
        catch (Exception e)
        {
            System.out.println("Failed to create connection: " + url + ", " + e.getMessage());
            e.printStackTrace();
        }
        return con;
    }
     */
    
    
    private String runUserCommand(String command,
            String testUser)
    {
        util.DEBUG("Execute '"+ command +"'");
        String output = "";
        
        try
        {
            Runtime rt = Runtime.getRuntime();
            Process proc = rt.exec(command);
            
            output = processOutput(proc);
            
            int exitVal = proc.waitFor();
            util.DEBUG("ExitValue: " + exitVal);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
        
        return output;
    }
    private String runUserCommand(String command,
            String testUser,
            // String dbDir,
            String id)
    {
        final String ID= "runUserCommand "+id+" ";
        util.DEBUG("Execute '"+ command +"'");
        String output = "";
        
        try
        {
            Runtime rt = Runtime.getRuntime();
            Process proc = rt.exec(command);
            
            output = processOutput(ID, proc);
            
            int exitVal = proc.waitFor();
            util.DEBUG("ExitValue: " + exitVal);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
        
        return output;
    }
    private void runUserCommandLocally(String command, String userDir, String ID)
    {
        util.DEBUG("");
        final String debugId = "runUserCommandLocally " + ID + " ";
        util.DEBUG("+++ runUserCommandLocally " + command + " / " + userDir);
                        
        String workingDirName = userDir;
        util.DEBUG(debugId+"user.dir: " + workingDirName);
        
        String tmp ="";
        util.DEBUG(debugId+command);
        
        final String fullCmd = command;
        
        String[] envElements = {""};
        tmp ="";
        for ( int i=0;i<envElements.length;i++)
        {tmp = tmp + envElements[i] + " ";}
        util.DEBUG(debugId+"envElements: " + tmp);
        
        final File workingDir = new File(workingDirName);
        
        String shellCmd = fullCmd;
        
        {
            final String localCommand = shellCmd;
            util.DEBUG(debugId+"localCommand: " + localCommand);
            
            try
            {
                Process proc = Runtime.getRuntime().exec(localCommand,envElements,workingDir);
                processDEBUGOutput(debugId+"pDo ", proc);
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
        
        util.DEBUG(debugId+"--- runUserCommandLocally ");
        util.DEBUG("");
        
    }
    private String runUserCommandRemotely(String command,
            String host,
            String testUser)
            throws InterruptedException
    {
        util.DEBUG("Execute '"+ command +"' on '"+ host +"'" + " as " + testUser);
        
        String localCommand = remoteShell + " "
                + "-l " + testUser + " " + host + " "
                + command
                // + "\"" + command + "\"" // make sure it's all run remotely
                ;
        return runUserCommand(localCommand,
                testUser);
        
    }
    private String runUserCommandRemotely(String command,
            String host,
            String testUser,
            // String dbDir,
            String id)
            throws InterruptedException
    {
        final String ID= "runUserCommandRemotely "+id+" ";
        util.DEBUG(ID+"Execute '"+ command +"' on '"+ host +"'" + " as " + testUser);
        
        
        String localCommand = remoteShell + " "
                + "-l " + testUser + " " + host + " "
                + command
                ;
        return runUserCommand(localCommand,
                testUser,
                // dbDir,
                ID);
    }
    
    private void runUserCommandInThread(String command,
            String testUser,
            String dbDir,
            String id)
            throws InterruptedException
    {
        util.DEBUG("");
        final String ID = "runUserCommandInThread "+id+" ";
        util.DEBUG(ID + "Execute '"+ command +"'");
        util.DEBUG("+++ "+ID);
     
        String localCommand =command ;
        util.DEBUG("runUserCommand: " + command );
     
        final String[] commandElements = {command};
        final String[] envElements = {"CLASS_PATH="+""
                , "PATH="+FS+"home"+FS+testUser+FS+"bin:$PATH"
                };
     
        String workingDirName = System.getProperty("user.dir");
        util.DEBUG("user.dir: " + workingDirName);
        String tmp ="";
        for ( int i=0;i<commandElements.length;i++)
        {tmp = tmp + commandElements[i];}
        util.DEBUG("commandElements: " + tmp);
        final String fullCmd = tmp;
        tmp ="";
        for ( int i=0;i<envElements.length;i++)
        {tmp = tmp + envElements[i] + " ";}
        util.DEBUG(ID+"envElements: " + tmp);
        final File workingDir = new File(workingDirName + FS + dbDir);
        util.DEBUG(ID+"workingDir: " + workingDirName + FS + dbDir);
     
        {
            util.DEBUG(
                    ID+"proc = Runtime.getRuntime().exec(commandElements,envElements,workingDir);"
                    );
     
            Thread cmdThread = new Thread(
                    new Runnable()
            {
                public void run()
                {
                    Process proc = null;
                    try
                    {
                        util.DEBUG(ID+"************** In run().");
                        proc = Runtime.getRuntime().exec(fullCmd,envElements,workingDir);
                        util.DEBUG(ID+"************** Done exec().");
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
     
                }
            }
            );
            util.DEBUG(ID+"************** Do .start().");
            cmdThread.start();
            cmdThread.join();
            util.DEBUG(ID+"************** Done .join().");
        }
     
        util.DEBUG(ID+"--- ");
        util.DEBUG("");
    }
    
    
    private void runUserCommandInThreadRemotely(String command,
            String host,
            String testUser,
            String dbDir,
            String id)
            throws InterruptedException
    {
        util.DEBUG("");
        final String ID=id+" runUserCommandInThreadRemotely ";
        util.DEBUG(ID+"+++ ");
        util.DEBUG(ID+"Execute '"+ command +"' on '"+ host +"'");
        
        util.DEBUG(ID + command
                + " @ " + host
                + " as " + testUser);
        
        /* final String[] commandElements = {remoteCmd
                , " -l"
                , " " + testUser
                , " " + host
                , " '" + command + "'"
                }; */
        final String[] envElements = {"CLASS_PATH="+""
                , "PATH="+FS+"home"+FS+testUser+FS+"bin:$PATH" // "/../bin"
        };
        
        String workingDirName = System.getProperty("user.dir");
        util.DEBUG(ID+"user.dir: " + workingDirName);
        String tmp ="";
        /* for ( int i=0;i<commandElements.length;i++)
        {tmp = tmp + commandElements[i];} */
        util.DEBUG(ID+"commandElements: " + tmp);
        final String fullCmd = command; // tmp;
        tmp ="";
        for ( int i=0;i<envElements.length;i++)
        {tmp = tmp + envElements[i] + " ";}
        util.DEBUG(ID+"envElements: " + tmp);
        final File workingDir = new File(workingDirName + FS + dbDir);
        util.DEBUG(ID+"workingDir: " + workingDirName + FS + dbDir);
        
        {
            util.DEBUG(ID+"Running command on non-local host "+ host);
            
            String[] shEnvElements = {"setenv CLASS_PATH "+""
                    , "setenv PATH "+FS+"home"+FS+testUser+FS+"bin:${PATH}"
            };
            String shellEnv = "";
            for ( int i=0;i<shEnvElements.length;i++)
            {shellEnv = shellEnv + shEnvElements[i] + ";";}
            util.DEBUG(ID+"shellEnv: " + shellEnv);
            
            String shellCmd = "cd " + workingDirName + FS + dbDir + ";pwd;"
                    + shellEnv + ";"
                    + fullCmd;
            
            util.DEBUG(ID+"shellCmd: " + shellCmd);
            
            final String localCommand = remoteShell + " "
                    + "-l " + testUser + " -n " + host + " "
                    + shellCmd
                    ;
            
            util.DEBUG(ID+"localCommand: " + localCommand);
            
            Thread serverThread = new Thread(
                    new Runnable()
            {
                public void run()
                {
                    Process proc = null;
                    try
                    {
                        util.DEBUG(ID+"************** In run().");
                        proc = Runtime.getRuntime().exec(localCommand,envElements,workingDir);
                        util.DEBUG(ID+"************** Done exec().");
                        processDEBUGOutput(ID, proc);
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                    
                }
            }
            );
            util.DEBUG(ID+"************** Do .start(). ");
            serverThread.start();
            // serverThread.join();
            // DEBUG(ID+"************** Done .join().");
            
        }
        
        util.DEBUG(ID+"--- ");
        util.DEBUG("");
    }
        
    void initEnvironment()
    throws IOException
    {
        System.out.println("*** ReplicationRun.initEnvironment -----------------------------------------");
        System.out.println("*** Properties -----------------------------------------");
        userDir = System.getProperty("user.dir");
        System.out.println("user.dir:          " + userDir);
        
        System.out.println("derby.system.home: " + System.getProperty("derby.system.home"));
        
        util.printDebug = true;
        System.out.println("printDebug: " + util.printDebug);
        
        showSysinfo = true;
        System.out.println("showSysinfo: " + showSysinfo);
        
        testUser = null;
        System.out.println("testUser: " + testUser);
        
        masterServerHost = "localhost";
        System.out.println("masterServerHost: " + masterServerHost);
        
        masterServerPort = 1527;
        System.out.println("masterServerPort: " + masterServerPort);
        
        slaveServerHost = "localhost";
        System.out.println("slaveServerHost: " + slaveServerHost);
        
        slaveServerPort = 4527;
        System.out.println("slaveServerPort: " + slaveServerPort);
        
        slaveReplPort = 8888;
        System.out.println("slaveReplPort: " + slaveReplPort);
        
        testClientHost = "localhost";
        System.out.println("testClientHost: " + testClientHost);
        
        masterDatabasePath = userDir;
        System.out.println("masterDatabasePath: " + masterDatabasePath);
        
        slaveDatabasePath = userDir;
        System.out.println("slaveDatabasePath: " + slaveDatabasePath);
        
        replicatedDb = "wombat";
        System.out.println("replicatedDb: " + replicatedDb);
        
        bootLoad = null;
        System.out.println("bootLoad: " + bootLoad);
        
        freezeDB = null;
        System.out.println("freezeDB: " + freezeDB);
        
        unFreezeDB = null;
        System.out.println("unFreezeDB: " + unFreezeDB);
        
        /* Done in subclasses
        replicationTest = "org.apache.derbyTesting.functionTests.tests.replicationTests.ReplicationTestRun";
        System.out.println("replicationTest: " + replicationTest);
        replicationVerify = "org.apache.derbyTesting.functionTests.tests.replicationTests.ReplicationTestRunVerify";
        System.out.println("replicationVerify: " + replicationVerify);
         */
        
        sqlLoadInit = null;
        System.out.println("sqlLoadInit: " + sqlLoadInit);

        
        specialTestingJar = null;
        System.out.println("specialTestingJar: " + specialTestingJar);
        
        jvmVersion = System.getProperty("java.home") +FS+"lib";
        System.out.println("jvmVersion: " + jvmVersion);
        
        masterJvmVersion = null;
        if ( masterJvmVersion == null )
        {masterJvmVersion = jvmVersion;}
        System.out.println("masterJvmVersion: " + masterJvmVersion);
        
        slaveJvmVersion = null;
        if ( slaveJvmVersion == null )
        {slaveJvmVersion = jvmVersion;}
        System.out.println("slaveJvmVersion: " + slaveJvmVersion);
        
        classPath = System.getProperty("java.class.path"); util.DEBUG("classPath: " + classPath);
        int sep = classPath.indexOf("derby.jar:"); util.DEBUG("sep: " + sep);
        String tclassPath = classPath.substring(0,sep); util.DEBUG("classPath: " + classPath);
        sep = tclassPath.lastIndexOf("/"); util.DEBUG("sep: " + sep);
        derbyVersion = tclassPath.substring(0,sep);
        System.out.println("derbyVersion: " + derbyVersion);
        
        derbyMasterVersion = null;
        if ( derbyMasterVersion == null )
        {derbyMasterVersion = derbyVersion;}
        System.out.println("derbyMasterVersion: " + derbyMasterVersion);
        
        derbySlaveVersion = null;
        if ( derbySlaveVersion == null )
        {derbySlaveVersion = derbyVersion;}
        System.out.println("derbySlaveVersion: " + derbySlaveVersion);
        
        String derbyTestingJar = derbyVersion + FS+"derbyTesting.jar";
        if ( specialTestingJar != null )  derbyTestingJar = specialTestingJar;
        System.out.println("derbyTestingJar: " + derbyTestingJar);
        
        junit_jar = derbyVersion + FS+"junit.jar";
        System.out.println("junit_jar: " + junit_jar);
        
        test_jars = derbyTestingJar
                + ":" + junit_jar;
        System.out.println("test_jars: " + test_jars);
        
        sleepTime = 15000;
        System.out.println("sleepTime: " + sleepTime);
        
        runUnReplicated = false;
        System.out.println("runUnReplicated: " + runUnReplicated);
        
        localEnv = false;
        System.out.println("localEnv: " + localEnv);
        
        derbyProperties = 
                 "derby.infolog.append=true"+LF
                +"derby.drda.logConnections=true"+LF
                +"derby.drda.traceAll=true"+LF;

        
        System.out.println("--------------------------------------------------------");
        
        masterPreRepl = null; // FIXME!
        masterPostRepl = null; // FIXME!
        slavePreSlave = null; // FIXME!
        masterPostSlave = null; // FIXME!
        slavePostSlave = null; // FIXME!
        
        System.out.println("--------------------------------------------------------");
        // for SimplePerfTest
        tuplesToInsert = 10000;
        commitFreq = 1000; // "0" is autocommit
        
        System.out.println("--------------------------------------------------------");
        
            // FIXME! state.initEnvironment(cp);
        
        System.out.println("--------------------------------------------------------");

    }
    
    void initMaster(String host, String dbName)
    throws Exception
    {
        
        util.DEBUG("initMaster");
        
        /* bootMasterDataBase now does "connect ...;create=true" */
        
        String results = null;
        if ( host.equalsIgnoreCase("localhost") || localEnv )
        {
            String dir = masterDatabasePath+FS+masterDbSubPath;
            util.mkDirs(dir); // Create the dir if non-existing.
            util.cleanDir(dir, false); // false: do not delete the directory itself.
            
            // Ditto for slave:
            dir = slaveDatabasePath+FS+slaveDbSubPath;
            util.mkDirs(dir); // Create the dir if non-existing.
            util.cleanDir(dir, false); // false: do not delete the directory itself.
            
            // util.writeToFile(derbyProperties, dir+FS+"derby.properties");
        }
        else
        {
            String command = "cd "+masterDatabasePath+FS+masterDbSubPath+";"
                + " rm -rf " + dbName + " derby.log ij_master;"
                + " rm Server*.trace;"
                /* bootMasterDataBase now does "connect ...;create=true": */
               // + " cp -r " + masterDatabasePath + "/origdb/" + dbName +"/ .;"
                + " ls -al;"
                // Also cleanup on slave as part of this.
                + " cd "+slaveDatabasePath+FS+slaveDbSubPath+";"
                + " rm -rf " + dbName + " derby.log ij_slave;"
                + " rm Server*.trace;"
                + " ls -al;"
                ;
            results = 
                runUserCommandRemotely(command,
                host,
                testUser,
                // dbName, // unneccessary?
                "initMaster ");
        }
        System.out.println(results);
        
        
    }
    private void removeSlaveDBfiles(String host, String dbName)
    throws InterruptedException
    {
        /*
         * PoC:
         * cd /home/user/Replication/testing/db_slave
         * rm -f test/seg0/*
         */
        
        String command = "cd " + slaveDatabasePath+FS+slaveDbSubPath+";"
                + " rm -f " + dbName + FS + "seg0" + FS + "* ;"
                + " ls -al test test/seg0" // DEBUG
                ;
        
        String results =
                runUserCommandRemotely(command,
                host,
                testUser,
                // dbName, // unneccessary?
                "removeSlaveDBfiles ");
        System.out.println(results);
        
    }
    void initSlave(String host, String clientVM, String dbName)
    throws Exception
    {
        /*
         * PoC:
         * cd /home/user/Replication/testing/db_slave
         * rm -rf test derby.log ij_slave
         * cp -r ../db_master/test/ .
         */
        
        util.DEBUG("initSlave");
        
        String command = "cd " + slaveDatabasePath+FS+slaveDbSubPath+";"
                + " rm -rf " + dbName + " ij_slave;" // derby.log
                // + " rm Server*.trace;"
                + " cp -r " +masterDatabasePath+FS+masterDbSubPath+FS+dbName +"/ .;"
                + " ls -al" // DEBUG
                ;
        
        String results = null;
        if ( host.equalsIgnoreCase("localhost") || localEnv )
        {
            String slaveDir = slaveDatabasePath+FS+slaveDbSubPath;
            String slaveDb = slaveDatabasePath+FS+slaveDbSubPath+FS+dbName;
            // The slaveDb dir is cleaned by initMaster!
            // util.cleanDir(slaveDb, true); // true: do delete the db directory itself.
                                          // derby.log etc will be kept.
            String masterDb = masterDatabasePath+FS
                    +masterDbSubPath+FS+dbName;
            util.copyDir(masterDb, slaveDb); // Copy the master database 
                                         // directory (.../master/test) into the
                                         // slave directory (.../slave/).

            // util.writeToFile(derbyProperties, slaveDir+FS+"derby.properties");
        }
        else
        {
            results =
                runUserCommandRemotely(command,
                host,
                testUser,
                // dbName, // unneccessary?
                "initSlave ");
        }
        System.out.println(results);
        
        // Preliminary needs to freeze db before copying to slave and setting replication mode.
        // Should do: Assuming startMaster does unfreeze! 
        /* */
        util.DEBUG("*************************** DERBY-3384. Must manually unfreeze.");
        if ( host.equalsIgnoreCase("localhost") || localEnv )
        {
             String URL = DB_PROTOCOL
                +"://"+masterServerHost
                +":"+masterServerPort
                +FS+masterDatabasePath+FS+masterDbSubPath+FS+dbName;
            util.DEBUG("initSlave getConnection("+URL+")");
            Connection conn = DriverManager.getConnection(URL);
            Statement s = conn.createStatement();
            s.execute("call syscs_util.syscs_unfreeze_database()");
            conn.close();
        }
        else
        {
            runTest(unFreezeDB,
                    clientVM,
                    testClientHost,
                    masterServerHost, masterServerPort,
                    dbName
                    );
        }
         /* */

        
    }
    // ?? The following should be moved to a separate class, subclass this and
    // ?? CompatibilityCombinations
    void restartServer(String serverVM, String serverVersion,
            String serverHost,
            String interfacesToListenOn,
            int serverPort,
            String fullDbDirPath)
            throws Exception
    {
            stopServer(serverVM, serverVersion,
                    serverHost, serverPort);
            
            startServer(serverVM, serverVersion,
                    serverHost,
                    interfacesToListenOn, 
                    serverPort,
                    fullDbDirPath); // Distinguishing master/slave
    }
    NetworkServerControl startServer(String serverVM, String serverVersion,
            String serverHost,
            String interfacesToListenOn,
            int serverPort,
            String fullDbDirPath)
            throws Exception
    {
        util.DEBUG("");
        final String debugId = "startServer@" + serverHost + ":" + serverPort + " ";
        util.DEBUG(debugId+"+++ StartServer " + serverVM + " / " + serverVersion);
        
        String serverJvm = serverVM+JVMloc;
        String serverClassPath = serverVersion + FS+"derby.jar"
                + ":" + serverVersion + FS+"derbynet.jar";
        
        String command = "start";
        String securityOption = "";
        
        securityOption = "-noSecurityManager";
        
        final String[] commandElements = {serverJvm
                , " -Dderby.infolog.append=true"
                // , " -Dderby.language.logStatementText=true" // Goes into derby.log: Gets HUGE!
                , " -cp ", serverClassPath
                , " " + networkServerControl
                , " " + command
                , " -h ", interfacesToListenOn // allowedClients
                , " -p ", serverPort+""
                , " " + securityOption
                };
        final String[] envElements = {"CLASS_PATH="+serverClassPath
                , "PATH="+serverVM+FS+".."+FS+"bin"
                };
        
        String workingDirName = fullDbDirPath;
        util.DEBUG(debugId+"user.dir: " + workingDirName);
        String tmp ="";
        
        for ( int i=0;i<commandElements.length;i++)
        {tmp = tmp + commandElements[i];}
        util.DEBUG(debugId+"commandElements: " + tmp);
        
        final String fullCmd = tmp;
        tmp ="";
        for ( int i=0;i<envElements.length;i++)
        {tmp = tmp + envElements[i] + " ";}
        util.DEBUG(debugId+"envElements: " + tmp);
        
        final File workingDir = new File(workingDirName);
        util.DEBUG(debugId+"workingDir: " + workingDirName);
        
        if ( serverHost.equalsIgnoreCase("localhost") || localEnv )
        {
            // util.writeToFile(derbyProperties, fullDbDirPath+FS+"derby.properties");
        }
        
        String shellCmd = null;
        /*
        if ( serverHost.equalsIgnoreCase("localhost") )
        {
            // 1. Can not select jvm or Derby version in this case.
            // 2. Master and slave would run in same VM. Mixing derby.logs!
            return startServer_direct(serverHost, 
                    interfacesToListenOn, serverPort, 
                    fullDbDirPath,
                    securityOption);
        }
        else */
        if ( serverHost.equalsIgnoreCase("localhost") )
        {
            util.DEBUG(debugId+"Starting server on localhost "+ serverHost);
            shellCmd = fullCmd;
        }
        else
        {
            util.DEBUG(debugId+"Starting server on non-local host "+ serverHost);
            
            String[] shEnvElements = {"setenv CLASS_PATH "+serverClassPath
                    , "setenv PATH "+serverVM+FS+".."+FS+"bin:${PATH}"};
            String shellEnv = "";
            for ( int i=0;i<shEnvElements.length;i++)
            {shellEnv = shellEnv + shEnvElements[i] + ";";}
            util.DEBUG(debugId+"shellEnv: " + shellEnv);
            
            shellCmd = "cd " + workingDirName + ";pwd;"
                    + shellEnv + ";"
                    + fullCmd;
            
            util.DEBUG(debugId+"shellCmd: " + shellCmd);
            
            shellCmd = remoteShell + " "
                    + "-l " + testUser + " -n " + serverHost + " "
                    + shellCmd;
        }
        
        {
            final String localCommand = shellCmd;
            util.DEBUG(debugId+"localCommand: " + localCommand);
            
            Thread serverThread = new Thread(
                    new Runnable()
            {
                public void run()
                {
                    Process proc = null;
                    try
                    {
                        util.DEBUG(debugId+"************** In run().");
                        proc = Runtime.getRuntime().exec(localCommand,envElements,workingDir);
                        util.DEBUG(debugId+"************** Done exec().");
                        processDEBUGOutput(debugId+"pDo ", proc);
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                    
                }
            }
            );
            util.DEBUG(debugId+"************** Do .start().");
            serverThread.start();
            pingServer(serverHost, serverPort, 5); // Wait for the server to come up in a reasonable time....
            
        }
        
        util.DEBUG(debugId+"--- StartServer ");
        util.DEBUG("");
        return null;
    }
    private NetworkServerControl startServer_direct(String serverHost, 
            String interfacesToListenOn, 
            int serverPort, 
            String fullDbDirPath,
            String securityOption) // FIXME? true/false?
    throws Exception
    { // Wotk in progress. Not currently used! Only partly tested!
        util.DEBUG("startServer_direct " + serverHost 
                + " " + interfacesToListenOn +  " " + serverPort
                + " " + fullDbDirPath);
        assertTrue("Attempt to start server on non-localhost: " + serverHost, 
                serverHost.equalsIgnoreCase("localhost"));
        
        System.setProperty("derby.system.home", fullDbDirPath);
        System.setProperty("user.dir", fullDbDirPath);
        
        NetworkServerControl server = new NetworkServerControl(
                InetAddress.getByName(interfacesToListenOn), serverPort);
        
        server.start(null); 
        pingServer(serverHost, serverPort, 5);
        
        Properties sp = server.getCurrentProperties();
        sp.setProperty("noSecurityManager", 
                securityOption.equalsIgnoreCase("-noSecurityManager")?"true":"false");
        // derby.log for both master and slave ends up in masters system!
        // Both are run in the same VM! Not a good idea?
        return server;
    }


    void killMaster(String masterServerHost, int masterServerPort)
    throws InterruptedException
    {
        /* Problem doing getProperty()...
        stopServer(slaveJvmVersion, derbySlaveVersion,
                slaveServerHost, slaveServerPort);
         so instead */
        int pid = xFindServerPID(masterServerHost,masterServerPort);
        xStopServer(masterServerHost, pid);
    }
    void killSlave(String slaveServerHost, int slaveServerPort)
    throws InterruptedException
    {
        int pid = xFindServerPID(slaveServerHost, slaveServerPort);
        xStopServer(slaveServerHost, pid);
    }
    void destroySlaveDB(String slaveServerHost)
    throws InterruptedException
    {
        removeSlaveDBfiles(slaveServerHost, replicatedDb);
    }
    void stopServer(String serverVM, String serverVersion,
            String serverHost, int serverPort)
    {
        util.DEBUG("");
        final String debugId = "stopServer@" + serverHost + ":" + serverPort + " ";
        util.DEBUG("+++ stopServer " + serverVM + " / " + serverVersion
                + " " + debugId);
        
        String serverJvm = serverVM+JVMloc;
        String serverClassPath = serverVersion + FS+"derby.jar"
                + ":" + serverVersion + FS+"derbynet.jar";
        
        String command = "shutdown";
        int port = serverPort;
        
        final String[] commandElements = {serverJvm
                , " -Dderby.infolog.append=true"
                , " -cp ", serverClassPath
                , " " + networkServerControl
                , " " + command
                , " -h " + serverHost // FIXME! interfacesToListenOn
                , " -p ", serverPort+""
                // , " " + securityOption
                };
        final String[] envElements = {"CLASS_PATH="+serverClassPath
                , "PATH="+serverVM+FS+".."+FS+"bin"
                };
        
        String workingDirName = System.getProperty("user.dir"); // Means we will do the shutdown wherever we are
        util.DEBUG(debugId+"user.dir: " + workingDirName);
        
        String tmp ="";
        for ( int i=0;i<commandElements.length;i++)
        {tmp = tmp + commandElements[i];}
        util.DEBUG(debugId+"commandElements: " + tmp);
        
        final String fullCmd = tmp;
        tmp ="";
        for ( int i=0;i<envElements.length;i++)
        {tmp = tmp + envElements[i] + " ";}
        util.DEBUG(debugId+"envElements: " + tmp);
        
        final File workingDir = new File(workingDirName);
        
        String shellCmd = null;
        
        if ( serverHost.equalsIgnoreCase("localhost") )
        {
            util.DEBUG(debugId+"Stopping server on localhost "+ serverHost);
            shellCmd = fullCmd;
        }
        else
        {
            util.DEBUG(debugId+"Stopping server on non-local host "+ serverHost);
            
            String[] shEnvElements = {"setenv CLASS_PATH "+serverClassPath
                    , "setenv PATH "+serverVM+FS+".."+FS+"bin:${PATH}"
                    };
            String shellEnv = "";
            for ( int i=0;i<shEnvElements.length;i++)
            {shellEnv = shellEnv + shEnvElements[i] + ";";}
            util.DEBUG(debugId+"shellEnv: " + shellEnv);
            
            shellCmd = "pwd;" + shellEnv + ";"  + fullCmd;
            util.DEBUG(debugId+"shellCmd: " + shellCmd);
            
            shellCmd = remoteShell + " "
                    + "-l " + testUser + " -n " + serverHost + " "
                    + shellCmd;
        }
        
        {
            final String localCommand = shellCmd;
            util.DEBUG(debugId+"localCommand: " + localCommand);
            
            try
            {
                Process proc = Runtime.getRuntime().exec(localCommand,envElements,workingDir);
                processDEBUGOutput(debugId+"pDo ", proc);
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
        
        util.DEBUG(debugId+"--- stopServer ");
        util.DEBUG("");
        
    }
    
    private void processOutput(String id, Process proc, PrintWriter out)
    throws Exception
    {
        InputStream serveInputStream = proc.getInputStream();
        InputStream serveErrorStream = proc.getErrorStream();
        
        InputStreamReader isr = new InputStreamReader(serveInputStream);
        InputStreamReader esr = new InputStreamReader(serveErrorStream);
        BufferedReader bir = new BufferedReader(isr);
        BufferedReader ber = new BufferedReader(esr);
        String line=null;
        util.DEBUG(id+"---- out:", out);
        while ( (line = bir.readLine()) != null)
        {
            out.println(id+line);
        }
        util.DEBUG(id+"---- err:",out);
        while ( (line = ber.readLine()) != null)
        {
            out.println(id+line);
        }
        util.DEBUG(id+"----     ",out);
        
    }
    
    private String processOutput(Process proc)
    throws Exception
    {
        InputStream serveInputStream = proc.getInputStream();
        InputStream serveErrorStream = proc.getErrorStream();
        
        InputStreamReader isr = new InputStreamReader(serveInputStream);
        InputStreamReader esr = new InputStreamReader(serveErrorStream);
        BufferedReader bir = new BufferedReader(isr);
        BufferedReader ber = new BufferedReader(esr);
        String line=null;
        String result = null;
        util.DEBUG("---- out:");
        // Would skip first line! if ( bir.readLine() != null ) {result = "";}
        while ( (line = bir.readLine()) != null)
        {
            util.DEBUG(line);
            if ( result == null )
            {result = line;}
            else
            {result = result + LF + line;}
        }
        util.DEBUG("---- err:");
        // Would skip first line! if ( ber.readLine() != null ) {result = result + LF;}
        while ( (line = ber.readLine()) != null)
        {
            util.DEBUG(line);
            // result = result + LF + line;
            if ( result == null )
            {result = line;}
            else
            {result = result + LF + line;}
        }
        util.DEBUG("----     ");
        
        return result;
    }
    private String processOutput(String id, Process proc)
    throws Exception
    {
        InputStream serveInputStream = proc.getInputStream();
        InputStream serveErrorStream = proc.getErrorStream();
        
        InputStreamReader isr = new InputStreamReader(serveInputStream);
        InputStreamReader esr = new InputStreamReader(serveErrorStream);
        BufferedReader bir = new BufferedReader(isr);
        BufferedReader ber = new BufferedReader(esr);
        String line=null;
        String result = null;
        util.DEBUG(id+"---- out:");
        // if ( bir.readLine() != null ) {result = id+" ---- out:";}
        while ( (line = bir.readLine()) != null)
        {
            util.DEBUG(id+line);
            result = result + LF + line;
        }
        util.DEBUG(id+"---- err:");
        // if ( ber.readLine() != null ) {result = result + LF + id+" ---- err:";}
        while ( (line = ber.readLine()) != null)
        {
            util.DEBUG(id+line);
            result = result + LF + line;
        }
        util.DEBUG(id+"----     ");
        // result = result + LF + id+" ----";
        
        return result;
    }
    
    private void processDEBUGOutput(String id, Process proc)
    throws Exception
    {
        InputStream serveInputStream = proc.getInputStream();
        InputStream serveErrorStream = proc.getErrorStream();
        
        InputStreamReader isr = new InputStreamReader(serveInputStream);
        InputStreamReader esr = new InputStreamReader(serveErrorStream);
        BufferedReader bir = new BufferedReader(isr);
        BufferedReader ber = new BufferedReader(esr);
        String line=null;
        util.DEBUG(id+"---- out:");
        while ( (line = bir.readLine()) != null)
        {
            util.DEBUG(id+line);
        }
        util.DEBUG(id+"---- err:");
        while ( (line = ber.readLine()) != null)
        {
            util.DEBUG(id+line);
        }
        util.DEBUG(id+"----     ");
        
    }
    private void processDEBUGOutput(String id, Process proc, PrintWriter out)
    throws Exception
    {
        InputStream serveInputStream = proc.getInputStream();
        InputStream serveErrorStream = proc.getErrorStream();
        
        InputStreamReader isr = new InputStreamReader(serveInputStream);
        InputStreamReader esr = new InputStreamReader(serveErrorStream);
        BufferedReader bir = new BufferedReader(isr);
        BufferedReader ber = new BufferedReader(esr);
        String line=null;
        util.DEBUG(id+"---- out:", out);
        while ( (line = bir.readLine()) != null)
        {
            out.println(id+line);
        }
        util.DEBUG(id+"---- err:",out);
        while ( (line = ber.readLine()) != null)
        {
            out.println(id+line);
        }
        util.DEBUG(id+"----     ",out);
        
    }
    
    private void pingServer( String hostName, int port, int iterations)
    throws Exception
    {
        util.DEBUG("+++ pingServer");
        ping( new NetworkServerControl(InetAddress.getByName(hostName),port), iterations);
        util.DEBUG("--- pingServer");
    }
    
    private	static void	ping( NetworkServerControl controller, int iterations )
    throws Exception
    {
        Exception	finalException = null;
        
        for ( int i = 0; i < iterations; i++ )
        {
            try
            {
                controller.ping();
                // DEBUG("Server came up in less than " + i*(SLEEP_TIME_MILLIS/1000) + " secs.");
                return;
            }
            catch (Exception e)
            { finalException = e; }
            
            Thread.sleep( SLEEP_TIME_MILLIS );
        }
        
        System.out.println( "Server did not come up: " + finalException.getMessage() );
        finalException.printStackTrace();
        
    }

    void startOptionalLoad(Load load,
            String dbSubPath,
            String serverHost,
            int serverPort)
            throws Exception
    {
        startLoad(load.load,
                dbSubPath,
                load.database,
                load.existingDB,
                load.clientHost,
                serverHost,
                serverPort);
    }
    void startLoad(String load,
            String dbSubPath,
            String database,
            boolean existingDB,
            String testClientHost,
            String serverHost,
            int serverPort)
            throws Exception
    {
        util.DEBUG("run load " + load
                + " on client " + testClientHost
                + " against server " + serverHost + ":" + serverPort
                + " using DB  " + database + "["+existingDB+"]"
                );
        if ( load == null )
        {
            System.out.println("No load supplied!");
            return;
        }
        if ( !existingDB )
        {
            // Create it!
            String URL = DB_PROTOCOL
                    +"://"+serverHost
                    +":"+serverPort
                    +FS+masterDatabasePath+FS+dbSubPath+FS+database // FIXME! for slave load!
                    +";create=true"; // Creating!
            String ijClassPath = derbyVersion +FS+ "derbyclient.jar"
                    + ":" + derbyVersion +FS+ "derbyTesting.jar"
                    + ":" + derbyVersion +FS+ "derbytools.jar";
            String clientJvm = jvmVersion+JVMloc;
            String command = "rm -rf /"+masterDatabasePath+FS+dbSubPath+FS+database+";" // FIXME! for slave load!
                    + clientJvm // "java"
                    + " -Dij.driver=" + DRIVER_CLASS_NAME
                    + " -Dij.connection.create"+database+"=\"" + URL + "\""
                    + " -classpath " + ijClassPath + " org.apache.derby.tools.ij"
                    + " " + sqlLoadInit // FIXME! Should be load specific!
                    ;
            String results =
                    runUserCommandRemotely(command,
                    testClientHost,
                    testUser,
                    "Create_"+database);
            
        }
        
        // Must run in separate thread!:
        runLoad(load,
                jvmVersion,
                testClientHost,
                serverHost, serverPort,
                dbSubPath+FS+database);
        
        // FIXME! How to join and cleanup....
        
    }

    ///////////////////////////////////////////////////////////////////////////
    /* Remove any servers or tests still running
     */
    void cleanAllTestHosts()
    {
        System.out.println("************************** cleanAllTestHosts() Not yet implemented");
    }

    ///////////////////////////////////////////////////////////////////////////
    /* The following is used to run tests in the various states of replication
     */
    class State
    {
        String testPreStartedMasterServer = null;
        boolean testPreStartedMasterServerReturn = false;
        String testPreStartedSlaveServer = null;
        boolean testPreStartedSlaveServerReturn = false;
        String testPreStartedMaster = null;
        boolean testPreStartedMasterReturn = false;
        String testPreInitSlave = null;
        boolean testPreInitSlaveReturn = false;
        String testPreStartedSlave = null;
        boolean testPreStartedSlaveReturn = false;
        String testPostStartedMasterAndSlave = null;
        boolean testPostStartedMasterAndSlaveReturn = false;
        String testPreStoppedMaster = null;
        boolean testPreStoppedMasterReturn = false;
        String testPreStoppedMasterServer = null;
        boolean testPreStoppedMasterServerReturn = false;
        String testPreStoppedSlave = null;
        boolean testPreStoppedSlaveReturn = false;
        String testPreStoppedSlaveServer = null;
        boolean testPreStoppedSlaveServerReturn = false;
        String testPostStoppedSlave = null;
        boolean testPostStoppedSlaveReturn = false;
        String testPostStoppedSlaveServer = null;
        boolean testPostStoppedSlaveServerReturn = false;
                
        void initEnvironment(Properties cp)
        {
            testPreStartedMasterServer = cp.getProperty("test.PreStartedMasterServer", null);
            testPreStartedMasterServerReturn = cp.getProperty("test.PreStartedMasterServer.return", "false")
                                                             .equalsIgnoreCase("true");
            util.DEBUG("testPreStartedMasterServer:" 
                    + testPreStartedMasterServer + FS 
                    + testPreStartedMasterServerReturn);
            
            testPreStartedSlaveServer = cp.getProperty("test.PreStartedSlaveServer", null);
            testPreStartedSlaveServerReturn = cp.getProperty("test.PreStartedSlaveServer.return", "false")
                                                             .equalsIgnoreCase("true");
            util.DEBUG("testPreStartedSlaveServer:" 
                    + testPreStartedSlaveServer + FS 
                    + testPreStartedSlaveServerReturn);
            
            testPreInitSlave = cp.getProperty("test.PreInitSlave", null);
            testPreInitSlaveReturn = cp.getProperty("test.PreInitSlave.return", "false")
                                                             .equalsIgnoreCase("true");
            util.DEBUG("testPreInitSlave:" 
                    + testPreInitSlave + FS 
                    + testPreInitSlaveReturn);
            
            testPreStartedMaster = cp.getProperty("test.PreStartedMaster", null);
            testPreStartedMasterReturn = cp.getProperty("test.PreStartedMaster.return", "false")
                                                             .equalsIgnoreCase("true");
            util.DEBUG("testPreStartedMaster:" 
                    + testPreStartedMaster + FS 
                    + testPreStartedMasterReturn);
            
            testPreStartedSlave = cp.getProperty("test.PreStartedSlave", null);
            testPreStartedSlaveReturn = cp.getProperty("test.PreStartedSlave.return", "false")
                                                             .equalsIgnoreCase("true");
            util.DEBUG("testPreStartedSlave:" 
                    + testPreStartedSlave + FS 
                    + testPreStartedSlaveReturn);
            
            testPostStartedMasterAndSlave = cp.getProperty("test.PostStartedMasterAndSlave", null);
            testPostStartedMasterAndSlaveReturn = cp.getProperty("test.PostStartedMasterAndSlave.return", "false")
                                                             .equalsIgnoreCase("true");
            util.DEBUG("testPostStartedMasterAndSlave:" 
                    + testPostStartedMasterAndSlave + FS 
                    + testPostStartedMasterAndSlaveReturn);
            
            testPreStoppedMaster = cp.getProperty("test.PreStoppedMaster", null);
            testPreStoppedMasterReturn = cp.getProperty("test.PreStoppedMaster.return", "false")
                                                             .equalsIgnoreCase("true");
            util.DEBUG("testPreStoppedMaster:" 
                    + testPreStoppedMaster + FS 
                    + testPreStoppedMasterReturn);
            
            testPreStoppedMasterServer = cp.getProperty("test.PreStoppedMasterServer", null);
            testPreStoppedMasterServerReturn = cp.getProperty("test.PreStoppedMasterServer.return", "false")
                                                             .equalsIgnoreCase("true");
            util.DEBUG("testPreStoppedMasterServer:" 
                    + testPreStoppedMasterServer + FS 
                    + testPreStoppedMasterServerReturn);
            
            testPreStoppedSlave = cp.getProperty("test.PreStoppedSlave", null);
            testPreStoppedSlaveReturn = cp.getProperty("test.PreStoppedSlave.return", "false")
                                                             .equalsIgnoreCase("true");
            util.DEBUG("testPreStoppedSlave:" 
                    + testPreStoppedSlave + FS 
                    + testPreStoppedSlaveReturn);
            
            testPostStoppedSlave = cp.getProperty("test.PostStoppedSlave", null);
            testPostStoppedSlaveReturn = cp.getProperty("test.PostStoppedSlave.return", "false")
                                                             .equalsIgnoreCase("true");
            util.DEBUG("testPostStoppedSlave:" 
                    + testPostStoppedSlave + FS 
                    + testPostStoppedSlaveReturn);
            
            testPostStoppedSlaveServer = cp.getProperty("test.PostStoppedSlaveServer", null);
            testPostStoppedSlaveServerReturn = cp.getProperty("test.PostStoppedSlaveServer.return", "false")
                                                             .equalsIgnoreCase("true");
            util.DEBUG("testPostStoppedSlaveServer:" 
                    + testPostStoppedSlaveServer + FS 
                    + testPostStoppedSlaveServerReturn);
        }
        
    boolean testPreStartedMasterServer()
        throws Exception
    {
        /*
# Superflueous? Set .test=null for false? test.preStartedMasterServer=true
# Test to run:
test.preStartedMasterServer.test=org.apache.derbyTesting.functionTests.tests.replicationTests.StartMasterCmdTooEarly
# Return from test framework immediatly:
test.preStartedMasterServer.return=true
         */
        util.DEBUG("****** BEGIN testPreStartedMasterServer");
        if ( testPreStartedMasterServer != null )
        {
            runStateTest(testPreStartedMasterServer, // E.g. org.apache.derbyTesting.functionTests.tests.replicationTests.TestPreStartedMasterServer
                    jvmVersion,
                    testClientHost, // using connect. On masterServerHost using CLI
                    masterServerHost, masterServerPort,
                    replicatedDb);
        }
        if ( testPreStartedMasterServerReturn ) cleanupAndShutdown();
        util.DEBUG("****** END   testPreStartedMasterServer");
        return testPreStartedMasterServerReturn;
    }

    boolean testPreStartedSlaveServer()
        throws Exception
    {
        /*
# test.preStartedSlaveServer=true
test.preStartedSlaveServer.test=org.apache.derbyTesting.functionTests.tests.replicationTests.StartSlaveCmdTooEarly
test.preStartedSlaveServer.return=true
         */
        util.DEBUG("****** BEGIN testPreStartedSlaveServer");
        if ( testPreStartedSlaveServer != null )
        {
            runStateTest(testPreStartedSlaveServer, // E.g. org.apache.derbyTesting.functionTests.tests.replicationTests.TestPreStartedSlaveServer
                    jvmVersion,
                    testClientHost, // using connect. On slaveServerHost using CLI
                    masterServerHost, masterServerPort,
                    replicatedDb);
        }
        if ( testPreStartedSlaveServerReturn ) cleanupAndShutdown();
        util.DEBUG("****** END   testPreStartedSlaveServer");
        return testPreStartedSlaveServerReturn;
    }

    boolean testPreStartedMaster()
        throws Exception
    {
        /*
# test.preStartedMaster=true
test.preStartedMaster.test=org.apache.derbyTesting.functionTests.tests.replicationTests.StartMasterCmd_OK
# test.preStartedMaster.test=org.apache.derbyTesting.functionTests.tests.replicationTests.StartMasterCmd_ERR
test.preStartedMaster.return=true
         */
        util.DEBUG("****** BEGIN testPreStartedMaster");
        if ( testPreStartedMaster != null )
        {
            runStateTest(testPreStartedMaster, // E.g. org.apache.derbyTesting.functionTests.tests.replicationTests.TestPreStartedMaster
                    jvmVersion,
                    testClientHost, // using connect. On masterServerHost using CLI
                    masterServerHost, masterServerPort,
                    replicatedDb);
        }
        if ( testPreStartedMasterReturn ) cleanupAndShutdown();
        util.DEBUG("****** END   testPreStartedMaster");
        return testPreStartedMasterReturn;
    }

    boolean testPreInitSlave()
        throws Exception
    {
        /*
# test.preInitSlave=true
test.preInitSlave.test=org.apache.derbyTesting.functionTests.tests.replicationTests.StartSlaveCmd_OK
# test.preInitSlave.test=org.apache.derbyTesting.functionTests.tests.replicationTests.StartSlaveCmd_ERR
test.preInitSlave.return=true
         */
        util.DEBUG("****** BEGIN testPreInitSlave");
        if ( testPreInitSlave != null )
        {
            runStateTest(testPreInitSlave,
                    jvmVersion,
                    testClientHost, // using connect. On slaveServerHost using CLI
                    masterServerHost, masterServerPort,
                    replicatedDb);
        }
        if ( testPreInitSlaveReturn ) cleanupAndShutdown();
        util.DEBUG("****** END   testPreInitSlave");
        return testPreInitSlaveReturn;
    }

    boolean testPreStartedSlave()
        throws Exception
    {
        /*
# test.preStartedSlave=true
test.preStartedSlave.test=org.apache.derbyTesting.functionTests.tests.replicationTests.StartSlaveCmd_OK
# test.preStartedMaster.test=org.apache.derbyTesting.functionTests.tests.replicationTests.StartSlaveCmd_ERR
test.preStartedSlave.return=true
         */
        util.DEBUG("****** BEGIN testPreStartedSlave");
        if ( testPreStartedSlave != null )
        {
            runStateTest(testPreStartedSlave,
                    jvmVersion,
                    testClientHost, // using connect. On slaveServerHost using CLI
                    masterServerHost, masterServerPort,
                    replicatedDb);
        }
        if ( testPreStartedSlaveReturn ) cleanupAndShutdown();
        util.DEBUG("****** END   testPreStartedSlave");
        return testPreStartedSlaveReturn;
    }

    boolean testPostStartedMasterAndSlave()
        throws Exception
    {
        /*
# test.postStartedMasterAndSlave=true
test.postStartedMasterAndSlave.test=org.apache.derbyTesting.functionTests.tests.replicationTests.XXXX_OK
# test.postStartedMasterAndSlave.test=org.apache.derbyTesting.functionTests.tests.replicationTests.XXXX_ERR
test.postStartedMasterAndSlave.return=true
         */
        util.DEBUG("****** BEGIN testPostStartedMasterAndSlave");
        if ( testPostStartedMasterAndSlave != null )
        {
            // run testPostStartedMasterAndSlave test
            runStateTest(testPostStartedMasterAndSlave,
                    jvmVersion,
                    testClientHost, // using connect. On slaveServerHost using CLI
                    masterServerHost, masterServerPort,
                    replicatedDb);
        }
        if ( testPostStartedMasterAndSlaveReturn ) cleanupAndShutdown();
        util.DEBUG("****** END   testPostStartedMasterAndSlave");
        return testPostStartedMasterAndSlaveReturn;
    }

    boolean testPreStoppedMaster()
        throws Exception
    {
        /*
# test.preStoppedMaster=true
test.preStoppedMaster.test=org.apache.derbyTesting.functionTests.tests.replicationTests.StopMasterCmd_OK
# test.preStoppedMaster.test=org.apache.derbyTesting.functionTests.tests.replicationTests.StopMasterCmd_ERR
test.preStoppedMaster.return=true
         */
        util.DEBUG("****** BEGIN testPreStoppedMaster");
        if ( testPreStoppedMaster != null )
        {
            runStateTest(testPreStoppedMaster,
                    jvmVersion,
                    testClientHost, // using connect. On slaveServerHost using CLI
                    masterServerHost, masterServerPort,
                    replicatedDb);
        }
        if ( testPreStoppedMasterReturn ) cleanupAndShutdown();
        util.DEBUG("****** END   testPreStoppedMaster");
        return testPreStoppedMasterReturn;
    }

    boolean testPreStoppedMasterServer()
        throws Exception
    {
        /*
# test.preStoppedMasterServer=true
test.preStoppedMasterServer.test=org.apache.derbyTesting.functionTests.tests.replicationTests.YYYYY_OK
# test.preStoppedMasterServer.test=org.apache.derbyTesting.functionTests.tests.replicationTests.YYYYY_ERR
test.preStoppedMasterServer.return=true
         */
        util.DEBUG("****** BEGIN testPreStoppedMasterServer");
        if ( testPreStoppedMasterServer != null )
        {
            runStateTest(testPreStoppedMasterServer,
                    jvmVersion,
                    testClientHost, // using connect. On slaveServerHost using CLI
                    masterServerHost, masterServerPort,
                    replicatedDb);
        }
        if ( testPreStoppedMasterServerReturn ) cleanupAndShutdown();
        util.DEBUG("****** END   testPreStoppedMasterServer");
        return testPreStoppedMasterServerReturn;
    }

    boolean testPreStoppedSlave()
        throws Exception
    {
        /*
# test.preStoppedSlave=true
test.preStoppedSlave.test=org.apache.derbyTesting.functionTests.tests.replicationTests.StopSlaveCmd_OK
# test.preStoppedSlave.test=org.apache.derbyTesting.functionTests.tests.replicationTests.StopSlaveCmd_ERR
test.preStoppedSlave.return=true
         */
        util.DEBUG("****** BEGIN testPreStoppedSlave");
        if ( testPreStoppedSlave != null )
        {
            runStateTest(testPreStoppedSlave,
                    jvmVersion,
                    testClientHost, // using connect. On slaveServerHost using CLI
                    masterServerHost, masterServerPort,
                    replicatedDb);
        }
        if ( testPreStoppedSlaveReturn ) cleanupAndShutdown();
        util.DEBUG("****** END   testPreStoppedSlave");
        return testPreStoppedSlaveReturn;
    }

    boolean testPreStoppedSlaveServer()
        throws Exception
    {
        /*
# test.preStoppedSlaveServer=true
test.preStoppedSlaveServer.test=org.apache.derbyTesting.functionTests.tests.replicationTests.ZZZZZZ_OK
# test.preStoppedSlaveServer.test=org.apache.derbyTesting.functionTests.tests.replicationTests.ZZZZZZ_ERR
test.preStoppedSlaveServer.return=true
         */
        util.DEBUG("****** BEGIN testPreStoppedSlaveServer");
        if ( testPreStoppedSlaveServer != null )
        {
            runStateTest(testPreStoppedSlaveServer,
                    jvmVersion,
                    testClientHost, // using connect. On slaveServerHost using CLI
                    masterServerHost, masterServerPort,
                    replicatedDb);
        }
        if ( testPreStoppedSlaveServerReturn ) cleanupAndShutdown();
        util.DEBUG("****** END   testPreStoppedSlaveServer");
        return testPreStoppedSlaveServerReturn;
    }

    boolean testPostStoppedSlaveServer()
        throws Exception
    {
        /*
# test.postStoppedSlaveServer=true
test.postStoppedSlaveServer.test=org.apache.derbyTesting.functionTests.tests.replicationTests.ZZZXXX_OK
# test.postStoppedSlaveServer.test=org.apache.derbyTesting.functionTests.tests.replicationTests.ZZZXXX_ERR
test.postStoppedSlaveServer.return=true
         */
        util.DEBUG("****** BEGIN testPostStoppedSlaveServer");
        if ( testPostStoppedSlaveServer != null )
        {
            runStateTest(testPostStoppedSlaveServer,
                    jvmVersion,
                    testClientHost, // using connect. On slaveServerHost using CLI
                    masterServerHost, masterServerPort,
                    replicatedDb);
        }
        if ( testPostStoppedSlaveServerReturn ) cleanupAndShutdown();
        util.DEBUG("****** END   testPostStoppedSlaveServer");
        return testPostStoppedSlaveServerReturn;
    }

        private void cleanupAndShutdown()
        {
            stopServer(jvmVersion, derbyVersion,
                masterServerHost, masterServerPort);

            stopServer(jvmVersion, derbyVersion,
                slaveServerHost, slaveServerPort);
        }
    }
    ///////////////////////////////////////////////////////////////////////////
    
    ///////////////////////////////////////////////////////////////////////////
    /* Load started in different states of replication. */
    static class Load
    {
        Load(String load, String database, boolean existingDB, String clientHost)
        {
            this.load = load; // .sql file or junit class
            this.database = database; // Database name used by load
            this.existingDB = existingDB; // Database already exists.
            this.clientHost = clientHost; // Host running load client.
        }
        Load(String id, Properties testRunProperties)
        {
            System.out.println("Load(): " + id);
            
            String pid = "test." + id;
            if ( testRunProperties.getProperty(pid,"false").equalsIgnoreCase("false") )
            {
                System.out.println(pid + " Not defined or set to false!");
            }
            else
            {
                
                pid = "test." + id + ".load";
                load = testRunProperties.getProperty(pid,
                        "org.apache.derbyTesting.functionTests.tests.replicationTests.DefaultLoad");
                System.out.println(pid+": " + load);
                
                pid = "test." + id + ".database";
                database = testRunProperties.getProperty(pid, id);
                System.out.println(pid+": " + database);
                
                pid = "test." + id + ".existingDB";
                existingDB = testRunProperties.getProperty(pid,"false").equalsIgnoreCase("true");
                System.out.println(pid+": " + existingDB);
                
                pid = "test." + id + ".clientHost";
                clientHost = testRunProperties.getProperty(pid, testClientHost);
                System.out.println(pid+": " + clientHost);
                
            }
            
        }
        
        String load = null; // .sql file or junit class
        String database = null; // Database name used by load
        boolean existingDB = false; // Database already exists.
        String clientHost = null; // Host running load client.
    }
    static Load masterPreRepl;
    static Load masterPostRepl;
    static Load slavePreSlave;
    static Load masterPostSlave;
    static Load slavePostSlave;
    ///////////////////////////////////////////////////////////////////////////
    
}
