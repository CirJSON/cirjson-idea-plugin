// This is a generated file. Not intended for manual editing.
package org.cirjson.plugin.idea.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CirJsonObjectIdElement extends PsiElement {

  @NotNull
  CirJsonIdKeyLiteral getIdKeyLiteral();

  @NotNull
  CirJsonStringLiteral getStringLiteral();

  @NotNull
  String getId();

}
