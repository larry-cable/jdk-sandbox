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

package jdk.jfr.events;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Enabled;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Period;

import static jdk.internal.usagelogger.UsageLogger.SystemProperties;

@Category({ "Usage Logging" })
@Description("Log JVM Usage/Invocation")
@Enabled(true)
@Label("Usage Logger")
@Name("jdk.UsageLogger")
@StackTrace(false)
@Period("endChunk")
public final class UsageLogEvent extends AbstractJDKEvent {

    // we create and populate the event here in order to encapsulate the dependency on jdk.internal.usagelogger.UsageLogger

    public static final void emitUsageLogEvent() {
	final var ule = new UsageLogEvent();

	//ule.begin();

        ule.jvmStartTime = SystemProperties.JVM_START_TIME.getValuesAsString();
        ule.hostname     = SystemProperties.HOSTNAME.getValuesAsString();
        ule.ipAddress    = SystemProperties.IP_ADDRESS.getValuesAsString();

        ule.pid          = SystemProperties.JVM_PID.getValuesAsString();
        ule.uuid         = SystemProperties.JVM_UUID.getValuesAsString();

        ule.javaHome     = SystemProperties.JAVA_HOME.getValuesAsString();
        ule.javaVersion  = SystemProperties.JAVA_VERSION.getValuesAsString();
        ule.javaVendor   = SystemProperties.JAVA_VENDOR.getValuesAsString();
        ule.jvmVersion   = SystemProperties.JAVA_VM_VERSION.getValuesAsString();
        ule.jvmVendor    = SystemProperties.JAVA_VM_VENDOR.getValuesAsString();

        ule.javaHome     = SystemProperties.JAVA_HOME.getValuesAsString();

        ule.javaArgs     = SystemProperties.JAVA_ARGUMENTS.getValuesAsString();

        ule.vmArgs       = SystemProperties.JVM_ARGS.getValuesAsString();

        ule.osName       = SystemProperties.OS_NAME.getValuesAsString();
        ule.osVersion    = SystemProperties.OS_VERSION.getValuesAsString();
        ule.osArch       = SystemProperties.OS_VERSION.getValuesAsString();

        ule.classpath    = SystemProperties.JAVA_CLASS_PATH.getValuesAsString();

        ule.modulePath      = SystemProperties.JDK_MODULE_PATH.getValuesAsString();
        ule.mainModule      = SystemProperties.JDK_MODULE_MAIN.getValuesAsString();
        ule.moduleMainClass = SystemProperties.JDK_MODULE_MAIN_CLASS.getValuesAsString();

        ule.userName     = SystemProperties.USER_NAME.getValuesAsString();
        ule.userDir      = SystemProperties.USER_DIR.getValuesAsString();
        //ule.userHome    = SystemProperties.USER_HOME.getValuesAsString();

	ule.commit();
    };

    @Label("JVM Start Time")
    public String jvmStartTime;

    @Label("Hostname")
    public String hostname;
    
    @Label("IP Address")
    public String ipAddress;
    
    @Label("jvm pid")
    public String pid;
    
    @Label("jvm uuid")
    public String uuid;

    @Label("java home")
    public String javaHome;
    
    @Label("Java Version")
    public String javaVersion;
    
    @Label("Java Vendor")
    public String javaVendor;
    
    @Label("jvm Version")
    public String jvmVersion;
    
    @Label("JVM Vendor")
    public String jvmVendor;
    
    @Label("arguments")
    public String javaArgs;
    
    @Label("vm arguments")
    public String vmArgs;
    
    @Label("classpath")
    public String classpath;

    @Label("modulepath")
    public String modulePath;

    @Label("main module")
    public String mainModule;

    @Label("module main class")
    public String moduleMainClass;

    @Label("O.S Name")
    public String osName;
    
    @Label("O.S Version")
    public String osVersion;
    
    @Label("O.S Architecture")
    public String osArch;
    
    @Label("user name")
    public String userName;
    
    @Label("user dir")
    public String userDir;
    
    //@Label("user home")
    //public String userHome;
}
