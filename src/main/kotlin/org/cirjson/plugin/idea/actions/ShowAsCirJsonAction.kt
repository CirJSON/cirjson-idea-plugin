package org.cirjson.plugin.idea.actions

import com.google.common.base.CharMatcher
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.reference.SoftReference
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.DocumentUtil
import org.cirjson.plugin.idea.CirJsonLanguage
import java.lang.ref.WeakReference

class ShowAsCirJsonAction : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val cirJsonLineExtractor = CirJsonLineExtractor(editor)

        if (!cirJsonLineExtractor.has()) {
            return
        }

        val fileEditorManager = FileEditorManager.getInstance(project)

        if (selectOpened(editor, cirJsonLineExtractor, fileEditorManager)) {
            return
        }

        val virtualFile =
                LightVirtualFile(StringUtil.trimMiddle(cirJsonLineExtractor.prefix, 50), CirJsonLanguage.INSTANCE,
                        cirJsonLineExtractor.get())
        virtualFile.putUserData(LINE_KEY, cirJsonLineExtractor.line)
        virtualFile.putUserData(EDITOR_REF_KEY, WeakReference(editor))

        val file = PsiManager.getInstance(project).findFile(virtualFile) ?: return

        DocumentUtil.writeInRunUndoTransparentAction {
            CodeStyleManager.getInstance(project).reformat(file, true)
        }

        virtualFile.isWritable = false
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val enabled =
                editor != null && e.getData(LangDataKeys.CONSOLE_VIEW) != null && CirJsonLineExtractor(editor).has()
        e.presentation.isEnabledAndVisible = enabled
    }

    private class CirJsonLineExtractor(editor: Editor) {

        var line = -1
            private set

        var lineStart = -1

        private var start = -1

        private var end = -1

        private val document = editor.document

        init {
            doCompute(editor)
        }

        private fun doCompute(editor: Editor) {
            val model = editor.selectionModel

            if (!model.hasSelection()) {
                val offset = editor.caretModel.offset

                if (offset <= document.textLength) {
                    line = document.getLineNumber(offset)
                    lineStart = document.getLineStartOffset(line)
                    getCirJsonString(document, document.getLineEndOffset(line))
                }

                return
            }

            lineStart = model.selectionStart
            val end = model.selectionEnd
            line = document.getLineNumber(lineStart)

            if (line == document.getLineNumber(end)) {
                getCirJsonString(document, end)
            }
        }

        private fun getCirJsonString(document: Document, lineEnd: Int) {
            val documentChars = document.charsSequence
            val start = CIRJSON_START_MATCHER.indexIn(documentChars, lineStart)

            if (start < 0) {
                return
            }

            var end = -1

            for (i in (lineEnd - 1) downTo (start + 1)) {
                if (documentChars[i] == '}') {
                    end = i
                    break
                }
            }

            if (end == -1) {
                return
            }

            this.start = start
            this.end = end + 1
        }

        fun has(): Boolean {
            return start != -1
        }

        val prefix: String
            get() {
                val chars = document.charsSequence
                var end = start

                for (i in (start - 1) downTo (lineStart + 1)) {
                    val c = chars[i]

                    if (c == ':' || c.isWhitespace()) {
                        end--
                    } else {
                        break
                    }
                }

                return CharMatcher.whitespace().trimFrom(chars.subSequence(lineStart, end))
            }

        fun get(): CharSequence {
            return document.charsSequence.subSequence(start, end + 1)
        }

    }

    companion object {

        private val LINE_KEY = Key.create<Int>("cirJsonFileToLogLineNumber")

        private val EDITOR_REF_KEY = Key.create<WeakReference<Editor>>("cirJsonFileToConsoleEditor")

        private val CIRJSON_START_MATCHER = CharMatcher.`is`('{')

        private fun selectOpened(editor: Editor, cirJsonLineExtractor: CirJsonLineExtractor,
                fileEditorManager: FileEditorManager): Boolean {
            for (fileEditor in fileEditorManager.allEditors) {
                if (fileEditor !is TextEditor) {
                    continue
                }

                val file = FileDocumentManager.getInstance().getFile(fileEditor.editor.document)

                if (file !is LightVirtualFile) {
                    continue
                }

                val line = LINE_KEY.get(file) ?: continue

                if (line == cirJsonLineExtractor.line) {
                    val editorReference = EDITOR_REF_KEY.get(file)

                    if (SoftReference.dereference(editorReference) === editor) {
                        fileEditorManager.openFile(file, true, true)
                        return true
                    }
                }
            }

            return false
        }

    }

}