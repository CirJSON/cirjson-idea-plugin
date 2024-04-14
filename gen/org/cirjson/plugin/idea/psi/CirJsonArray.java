// This is a generated file. Not intended for manual editing.
package org.cirjson.plugin.idea.psi;

import com.intellij.navigation.ItemPresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface CirJsonArray extends CirJsonContainer {

  @NotNull
  List<CirJsonValue> getValueList();

  @NotNull
  ItemPresentation getPresentation();

  @Nullable
  String getId();

  @Nullable
  CirJsonStringLiteral getIdLiteral();

}
