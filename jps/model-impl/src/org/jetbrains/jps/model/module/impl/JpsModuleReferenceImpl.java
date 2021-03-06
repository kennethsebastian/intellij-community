package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.impl.JpsNamedElementReferenceBase;
import org.jetbrains.jps.model.impl.JpsProjectElementReference;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;

/**
 * @author nik
 */
public class JpsModuleReferenceImpl extends JpsNamedElementReferenceBase<JpsModule, JpsModuleReferenceImpl> implements JpsModuleReference {
  public JpsModuleReferenceImpl(String elementName) {
    super(JpsModuleKind.MODULE_COLLECTION_KIND, elementName, new JpsProjectElementReference());
  }

  @NotNull
  @Override
  public JpsModuleReferenceImpl createCopy() {
    return new JpsModuleReferenceImpl(myElementName);
  }

  @NotNull
  @Override
  public String getModuleName() {
    return myElementName;
  }

  @Override
  public JpsModuleReference asExternal(@NotNull JpsModel model) {
    model.registerExternalReference(this);
    return this;
  }
}
