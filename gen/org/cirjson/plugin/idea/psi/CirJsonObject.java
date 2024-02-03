// This is a generated file. Not intended for manual editing.
package org.cirjson.plugin.idea.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.navigation.ItemPresentation;

public interface CirJsonObject extends CirJsonContainer {

  @Nullable
  CirJsonObjectIdElement getObjectIdElement();

  @NotNull
  List<CirJsonProperty> getPropertyList();

  @NotNull
  String getId();

  @Nullable
  CirJsonProperty findProperty(@NotNull String name);

  @NotNull
  ItemPresentation getPresentation();

}
