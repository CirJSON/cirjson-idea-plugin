// This is a generated file. Not intended for manual editing.
package org.cirjson.plugin.idea.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.navigation.ItemPresentation;

public interface CirJsonProperty extends CirJsonElement, PsiNamedElement {

  @NotNull
  String getName();

  @NotNull
  CirJsonValue getNameElement();

  @Nullable
  CirJsonValue getValue();

  @NotNull
  ItemPresentation getPresentation();

}
