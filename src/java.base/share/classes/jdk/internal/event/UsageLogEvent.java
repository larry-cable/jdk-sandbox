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

package jdk.internal.event;

//@Category("JVM Information")
//@Description("Log JVM Usage/Invocation")
//@Enabled()
//@Label("Java Usage Logger")
//@Name("jdk.UsageLogger")
//@StackTrace(false)
//@Period("endChunk")
//@MirrorEvent(className = "jdk.internal.event.UsageLogEvent")
public class UsageLogEvent extends Event {
	//@Label("timestamp")
	public String startTime;

	//@Label("hostname")
	public String hostname;
	
	//@Label("ip.address")
	public String ipAddress;
	
	//@Label("jvm pid")
	public String pid;
	
	//@Label("jvm uuid")
	public String uuid;

	//@Label("java home")
	public String javaHome;
	
	//@Label("Java Version")
	public String javaVersion;
	
	//@Label("Java Vendor")
	public String javaVendor;
	
	//@Label("jvm Version")
	public String jvmVersion;
	
	//@Label("JVM Vendor")
	public String jvmVendor;
	
	//@Label("arguments")
	public String javaArgs;
	
	//@Label("vm arguments")
	public String vmArgs;
	
	//@Label("classpath")
	public String classpath;

	//@Label("modulepath")
	public String modulePath;

	//@Label("main module")
	public String mainModule;

	//@Label("module main class")
	public String moduleMainClass;

	//@Label("O.S Name")
	public String osName;
	
	//@Label("O.S Version")
	public String osVersion;
	
	//@Label("O.S Architecture")
	public String osArch;
	
	//@Label("user name")
	public String userName;
	
	//@Label("user dir")
	public String userDir;
	
	//@Label("user home")
	public String userHome;
}
