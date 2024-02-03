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

public class CirJsonPropertyImpl extends CirJsonPropertyMixin implements CirJsonProperty {

  public CirJsonPropertyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CirJsonElementVisitor visitor) {
    visitor.visitProperty(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CirJsonElementVisitor) accept((CirJsonElementVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public String getName() {
    return CirJsonPsiImplUtils.getName(this);
  }

  @Override
  @NotNull
  public CirJsonValue getNameElement() {
    return CirJsonPsiImplUtils.getNameElement(this);
  }

  @Override
  @Nullable
  public CirJsonValue getValue() {
    return CirJsonPsiImplUtils.getValue(this);
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return CirJsonPsiImplUtils.getPresentation(this);
  }

}
