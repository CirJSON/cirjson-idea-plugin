// This is a generated file. Not intended for manual editing.
package org.cirjson.plugin.idea.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

public class CirJsonElementVisitor extends PsiElementVisitor {

  public void visitArray(@NotNull CirJsonArray o) {
    visitContainer(o);
  }

  public void visitBooleanLiteral(@NotNull CirJsonBooleanLiteral o) {
    visitLiteral(o);
  }

  public void visitContainer(@NotNull CirJsonContainer o) {
    visitValue(o);
  }

  public void visitIdKeyLiteral(@NotNull CirJsonIdKeyLiteral o) {
    visitPsiElement(o);
  }

  public void visitLiteral(@NotNull CirJsonLiteral o) {
    visitValue(o);
  }

  public void visitNullLiteral(@NotNull CirJsonNullLiteral o) {
    visitLiteral(o);
  }

  public void visitNumberLiteral(@NotNull CirJsonNumberLiteral o) {
    visitLiteral(o);
  }

  public void visitObject(@NotNull CirJsonObject o) {
    visitContainer(o);
  }

  public void visitObjectIdElement(@NotNull CirJsonObjectIdElement o) {
    visitPsiElement(o);
  }

  public void visitProperty(@NotNull CirJsonProperty o) {
    visitElement(o);
    // visitPsiNamedElement(o);
  }

  public void visitReferenceExpression(@NotNull CirJsonReferenceExpression o) {
    visitValue(o);
  }

  public void visitStringLiteral(@NotNull CirJsonStringLiteral o) {
    visitLiteral(o);
  }

  public void visitValue(@NotNull CirJsonValue o) {
    visitElement(o);
  }

  public void visitElement(@NotNull CirJsonElement o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
