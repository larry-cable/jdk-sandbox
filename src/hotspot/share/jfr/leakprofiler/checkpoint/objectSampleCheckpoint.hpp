/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

#ifndef SHARE_JFR_LEAKPROFILER_CHECKPOINT_OBJECTSAMPLECHECKPOINT_HPP
#define SHARE_JFR_LEAKPROFILER_CHECKPOINT_OBJECTSAMPLECHECKPOINT_HPP

#include "memory/allocation.hpp"

class EdgeStore;
class Klass;
class JavaThread;
class JfrCheckpointWriter;
class JfrStackTrace;
class JfrStackTraceRepository;
class ObjectSample;
class ObjectSampleMarker;
class ObjectSampler;
class Thread;

class ObjectSampleCheckpoint : AllStatic {
 public:
  static void on_klass_unload(const Klass* k);
  static void on_type_set_unload(JfrCheckpointWriter& writer);
  static void on_thread_exit(JavaThread* jt);
  static void resolve_sampled_objects();
  static void rotate(const ObjectSampler* sampler, JfrStackTraceRepository& repo);
  static void tag(const ObjectSample* sample);
  static bool tag(const JfrStackTrace* trace, JfrCheckpointWriter* writer = NULL);
  static int save_mark_words(const ObjectSampler* sampler, ObjectSampleMarker& marker, bool emit_all);
  static void write(const ObjectSampler* sampler, EdgeStore* edge_store, bool emit_all, Thread* thread);
};

#endif // SHARE_JFR_LEAKPROFILER_CHECKPOINT_OBJECTSAMPLECHECKPOINT_HPP
