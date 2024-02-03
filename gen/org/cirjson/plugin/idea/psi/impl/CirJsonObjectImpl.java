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

public class CirJsonObjectImpl extends CirJsonObjectMixin implements CirJsonObject {

  public CirJsonObjectImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull CirJsonElementVisitor visitor) {
    visitor.visitObject(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CirJsonElementVisitor) accept((CirJsonElementVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CirJsonObjectIdElement getObjectIdElement() {
    return findChildByClass(CirJsonObjectIdElement.class);
  }

  @Override
  @NotNull
  public List<CirJsonProperty> getPropertyList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CirJsonProperty.class);
  }

  @Override
  @Nullable
  public String getId() {
    return CirJsonPsiImplUtils.getId(this);
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return CirJsonPsiImplUtils.getPresentation(this);
  }

}
