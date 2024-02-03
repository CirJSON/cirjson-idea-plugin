// This is a generated file. Not intended for manual editing.
package org.cirjson.plugin.idea;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import org.cirjson.plugin.idea.psi.impl.*;

public interface CirJsonElementTypes {

  IElementType ARRAY = new CirJsonElementType("ARRAY");
  IElementType BOOLEAN_LITERAL = new CirJsonElementType("BOOLEAN_LITERAL");
  IElementType ID_KEY_LITERAL = new CirJsonElementType("ID_KEY_LITERAL");
  IElementType LITERAL = new CirJsonElementType("LITERAL");
  IElementType NULL_LITERAL = new CirJsonElementType("NULL_LITERAL");
  IElementType NUMBER_LITERAL = new CirJsonElementType("NUMBER_LITERAL");
  IElementType OBJECT = new CirJsonElementType("OBJECT");
  IElementType OBJECT_ID_ELEMENT = new CirJsonElementType("OBJECT_ID_ELEMENT");
  IElementType PROPERTY = new CirJsonElementType("PROPERTY");
  IElementType REFERENCE_EXPRESSION = new CirJsonElementType("REFERENCE_EXPRESSION");
  IElementType STRING_LITERAL = new CirJsonElementType("STRING_LITERAL");
  IElementType VALUE = new CirJsonElementType("VALUE");

  IElementType BLOCK_COMMENT = new CirJsonTokenType("BLOCK_COMMENT");
  IElementType COLON = new CirJsonTokenType(":");
  IElementType COMMA = new CirJsonTokenType(",");
  IElementType DOUBLE_QUOTED_STRING = new CirJsonTokenType("DOUBLE_QUOTED_STRING");
  IElementType FALSE = new CirJsonTokenType("false");
  IElementType IDENTIFIER = new CirJsonTokenType("IDENTIFIER");
  IElementType ID_KEY = new CirJsonTokenType("\"__cirJsonId__\"");
  IElementType LINE_COMMENT = new CirJsonTokenType("LINE_COMMENT");
  IElementType L_BRACKET = new CirJsonTokenType("[");
  IElementType L_CURLY = new CirJsonTokenType("{");
  IElementType NULL = new CirJsonTokenType("null");
  IElementType NUMBER = new CirJsonTokenType("NUMBER");
  IElementType R_BRACKET = new CirJsonTokenType("]");
  IElementType R_CURLY = new CirJsonTokenType("}");
  IElementType SINGLE_QUOTED_STRING = new CirJsonTokenType("SINGLE_QUOTED_STRING");
  IElementType TRUE = new CirJsonTokenType("true");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ARRAY) {
        return new CirJsonArrayImpl(node);
      }
      else if (type == BOOLEAN_LITERAL) {
        return new CirJsonBooleanLiteralImpl(node);
      }
      else if (type == ID_KEY_LITERAL) {
        return new CirJsonIdKeyLiteralImpl(node);
      }
      else if (type == NULL_LITERAL) {
        return new CirJsonNullLiteralImpl(node);
      }
      else if (type == NUMBER_LITERAL) {
        return new CirJsonNumberLiteralImpl(node);
      }
      else if (type == OBJECT) {
        return new CirJsonObjectImpl(node);
      }
      else if (type == OBJECT_ID_ELEMENT) {
        return new CirJsonObjectIdElementImpl(node);
      }
      else if (type == PROPERTY) {
        return new CirJsonPropertyImpl(node);
      }
      else if (type == REFERENCE_EXPRESSION) {
        return new CirJsonReferenceExpressionImpl(node);
      }
      else if (type == STRING_LITERAL) {
        return new CirJsonStringLiteralImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
