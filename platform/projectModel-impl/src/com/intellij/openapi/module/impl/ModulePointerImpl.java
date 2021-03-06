/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.module.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class ModulePointerImpl implements ModulePointer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.impl.ModulePointerImpl");
  private Module myModule;
  private String myModuleName;

  ModulePointerImpl(Module module) {
    myModule = module;
    myModuleName = null;
  }

  ModulePointerImpl(String name) {
    myModule = null;
    myModuleName = name;
  }

  public Module getModule() {
    return myModule;
  }

  @NotNull
  public String getModuleName() {
    if (myModule != null) {
      return myModule.getName();
    }
    else {
      return myModuleName;
    }
  }

  void moduleAdded(Module module) {
    LOG.assertTrue(myModule == null);
    LOG.assertTrue(myModuleName.equals(module.getName()));
    myModuleName = null;
    myModule = module;
  }

  void moduleRemoved(Module module) {
    LOG.assertTrue(myModule == module);
    myModuleName = myModule.getName();
    myModule = null;
  }

}
