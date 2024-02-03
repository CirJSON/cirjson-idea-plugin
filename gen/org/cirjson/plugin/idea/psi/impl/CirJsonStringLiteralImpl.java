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
import com.intellij.openapi.util.TextRange;
import kotlin.Pair;

public class CirJsonStringLiteralImpl extends CirJsonStringLiteralMixin implements CirJsonStringLiteral {

  public CirJsonStringLiteralImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull CirJsonElementVisitor visitor) {
    visitor.visitStringLiteral(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CirJsonElementVisitor) accept((CirJsonElementVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<Pair<TextRange, String>> getTextFragments() {
    return CirJsonPsiImplUtils.getTextFragments(this);
  }

  @Override
  @NotNull
  public String getValue() {
    return CirJsonPsiImplUtils.getValue(this);
  }

  @Override
  public boolean isPropertyName() {
    return CirJsonPsiImplUtils.isPropertyName(this);
  }

  @Override
  public boolean isId() {
    return CirJsonPsiImplUtils.isId(this);
  }

}
