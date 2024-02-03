// This is a generated file. Not intended for manual editing.
package org.cirjson.plugin.idea.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.util.TextRange;
import kotlin.Pair;

public interface CirJsonStringLiteral extends CirJsonLiteral {

  @NotNull
  List<Pair<TextRange, String>> getTextFragments();

  @NotNull
  String getValue();

  boolean isPropertyName();

  boolean isId();

}
