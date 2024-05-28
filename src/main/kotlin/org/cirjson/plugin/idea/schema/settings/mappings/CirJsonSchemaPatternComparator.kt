package org.cirjson.plugin.idea.schema.settings.mappings

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.BeforeAfter
import com.intellij.util.ThreeState
import org.cirjson.plugin.idea.schema.UserDefinedCirJsonSchemaConfiguration
import org.cirjson.plugin.idea.schema.remote.CirJsonFileResolver
import java.io.File

class CirJsonSchemaPatternComparator(private val myProject: Project) {

    fun isSimilar(leftItem: UserDefinedCirJsonSchemaConfiguration.Item,
            rightItem: UserDefinedCirJsonSchemaConfiguration.Item): ThreeState {
        return if (leftItem.isPattern != rightItem.isPattern) {
            ThreeState.NO
        } else if (leftItem.isPattern) {
            comparePatterns(leftItem, rightItem)
        } else {
            comparePaths(leftItem, rightItem)
        }
    }

    private fun comparePaths(leftItem: UserDefinedCirJsonSchemaConfiguration.Item,
            rightItem: UserDefinedCirJsonSchemaConfiguration.Item): ThreeState {
        val leftPath = leftItem.path
        val rightPath = rightItem.path

        if (CirJsonFileResolver.isTempOrMockUrl(leftPath) || CirJsonFileResolver.isTempOrMockUrl(rightPath)) {
            return if (leftPath == rightPath) {
                ThreeState.YES
            } else {
                ThreeState.NO
            }
        }

        val leftFile = File(myProject.basePath, leftPath)
        val rightFile = File(myProject.basePath, rightPath)

        if (leftItem.isDirectory) {
            if (FileUtil.isAncestor(leftFile, rightFile, true)) {
                return ThreeState.YES
            }
        }

        if (rightItem.isDirectory) {
            if (FileUtil.isAncestor(rightFile, leftFile, true)) {
                return ThreeState.YES
            }
        }

        return if (FileUtil.filesEqual(leftFile, rightFile) && leftItem.isDirectory == rightItem.isDirectory) {
            ThreeState.YES
        } else {
            ThreeState.NO
        }
    }

    companion object {

        private fun comparePatterns(leftItem: UserDefinedCirJsonSchemaConfiguration.Item,
                rightItem: UserDefinedCirJsonSchemaConfiguration.Item): ThreeState {
            if (leftItem.path == rightItem.path) {
                return ThreeState.YES
            }

            if (leftItem.path.indexOf(File.separatorChar) >= 0 || rightItem.path.indexOf(File.separatorChar) >= 0) {
                return ThreeState.NO
            }

            val left = getBeforeAfterAroundWildCards(leftItem.path)
            val right = getBeforeAfterAroundWildCards(rightItem.path)

            return if (left == null || right == null) {
                if (left == null && right == null) {
                    if (leftItem.path == rightItem.path) {
                        ThreeState.YES
                    } else {
                        ThreeState.NO
                    }
                } else if (left == null && right != null) {
                    checkOneSideWithoutWildcard(leftItem, right)
                } else if (left != null) {
                    checkOneSideWithoutWildcard(rightItem, left)
                } else {
                    // Never gets here
                    ThreeState.UNSURE
                }
            } else if (!StringUtil.isEmptyOrSpaces(left.before) && !StringUtil.isEmptyOrSpaces(right.before)) {
                if (left.before.startsWith(right.before) || right.before.startsWith(left.before)) {
                    ThreeState.YES
                } else {
                    ThreeState.NO
                }
            } else if (!StringUtil.isEmptyOrSpaces(left.after) && !StringUtil.isEmptyOrSpaces(right.after)) {
                if (left.after.endsWith(right.after) || right.after.endsWith(left.after)) {
                    ThreeState.YES
                } else {
                    ThreeState.NO
                }
            } else {
                ThreeState.UNSURE
            }
        }

        private fun getBeforeAfterAroundWildCards(pattern: String): BeforeAfter<String>? {
            val firstIdx = pattern.indexOf('*')
            val lastIdx = pattern.lastIndexOf('*')

            if (firstIdx < 0 || lastIdx < 0) {
                return null
            }

            return BeforeAfter(pattern.substring(0, firstIdx), pattern.substring(lastIdx + 1))
        }

        private fun checkOneSideWithoutWildcard(item: UserDefinedCirJsonSchemaConfiguration.Item,
                beforeAfter: BeforeAfter<String>): ThreeState {
            return if (!StringUtil.isEmptyOrSpaces(beforeAfter.before) && item.path.startsWith(beforeAfter.before)) {
                ThreeState.YES
            } else if (!StringUtil.isEmptyOrSpaces(beforeAfter.after) && item.path.endsWith(beforeAfter.after)) {
                ThreeState.YES
            } else {
                ThreeState.UNSURE
            }
        }

    }

}