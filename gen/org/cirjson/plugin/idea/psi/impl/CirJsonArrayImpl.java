// This is a generated file. Not intended for manual editing.
package org.cirjson.plugin.idea.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.cirjson.plugin.idea.psi.CirJsonArray;
import org.cirjson.plugin.idea.psi.CirJsonElementVisitor;
import org.cirjson.plugin.idea.psi.CirJsonStringLiteral;
import org.cirjson.plugin.idea.psi.CirJsonValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CirJsonArrayImpl extends CirJsonContainerImpl implements CirJsonArray {

  public CirJsonArrayImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull CirJsonElementVisitor visitor) {
    visitor.visitArray(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CirJsonElementVisitor) accept((CirJsonElementVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<CirJsonValue> getValueList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CirJsonValue.class);
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return CirJsonPsiImplUtils.getPresentation(this);
  }

  @Override
  @Nullable
  public String getId() {
    return CirJsonPsiImplUtils.getId(this);
  }

  @Override
  @Nullable
  public CirJsonStringLiteral getIdLiteral() {
    return CirJsonPsiImplUtils.getIdLiteral(this);
  }

}
