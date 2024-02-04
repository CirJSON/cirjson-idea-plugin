// This is a generated file. Not intended for manual editing.
package org.cirjson.plugin.idea.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.cirjson.plugin.idea.CirJsonElementTypes.*;
import org.cirjson.plugin.idea.psi.*;
import com.intellij.navigation.ItemPresentation;

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

}
