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

public class CirJsonNumberLiteralImpl extends CirJsonLiteralImpl implements CirJsonNumberLiteral {

  public CirJsonNumberLiteralImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull CirJsonElementVisitor visitor) {
    visitor.visitNumberLiteral(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CirJsonElementVisitor) accept((CirJsonElementVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  public double getValue() {
    return CirJsonPsiImplUtils.getValue(this);
  }

}
