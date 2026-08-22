package io.github.eonewg.gnome.ui.component

import android.content.Intent
import android.net.Uri

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import io.github.eonewg.gnome.data.model.MemoRepresentable
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.ui.page.common.LocalRootNavController
import io.github.eonewg.gnome.ui.page.common.RouteName
import io.github.eonewg.gnome.ui.media.MediaViewerActivity
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import io.github.eonewg.gnome.viewmodel.LocalUserState
import io.github.eonewg.gnome.util.findCustomTagMatches
import io.github.eonewg.gnome.util.getCustomTagName
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import java.net.URLEncoder
import kotlin.math.ceil

@Composable
fun MemoContent(
    memo: MemoRepresentable,
    previewMode: Boolean = false,
    checkboxChange: (checked: Boolean, startOffset: Int, endOffset: Int) -> Unit = { _, _, _ -> },
    isPreviewExpanded: Boolean = false,
    onPreviewExpandedChange: ((Boolean) -> Unit)? = null,
    selectable: Boolean = false,
    onTagClick: ((String) -> Unit)? = null
) {
    val rootNavController = LocalRootNavController.current
    val colors = GnomeDesign.colors
    val (text, previewed) = remember(memo.content, previewMode, isPreviewExpanded) {
        if (previewMode && !isPreviewExpanded) {
            extractPreviewContent(markdownText = memo.content)
        } else {
            Pair(memo.content, false)
        }
    }
    val handleTagClick = remember(rootNavController, onTagClick) {
        onTagClick ?: { tag ->
            rootNavController.navigate("${RouteName.TAG}/${URLEncoder.encode(tag, "UTF-8")}") {
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Column(
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
    ) {
        if (selectable || requiresRichMarkdown(text)) {
            Markdown(
                text,
                imageBaseUrl = LocalUserState.current.host,
                checkboxChange = checkboxChange,
                selectable = selectable,
                onTagClick = handleTagClick
            )
        } else {
            PlainMemoText(
                text = text,
                onTagClick = handleTagClick,
            )
        }

        MemoResourceContent(memo)

        if ((previewed || previewMode && isPreviewExpanded) && onPreviewExpandedChange != null) {
            Row(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    text = if (isPreviewExpanded) R.string.collapse.string else R.string.view_more.string,
                    color = colors.tagForeground,
                    style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.None),
                    modifier = Modifier.clickable {
                        onPreviewExpandedChange(!isPreviewExpanded)
                    }
                )
            }
        }
    }
}

@Composable
private fun PlainMemoText(
    text: String,
    onTagClick: (String) -> Unit,
) {
    val colors = GnomeDesign.colors
    val uriHandler = LocalUriHandler.current
    val tagStyle = TextLinkStyles(
        style = SpanStyle(
            color = colors.tagForeground,
            background = colors.tagBackground,
            textDecoration = TextDecoration.None,
        )
    )
    val linkListener = remember(onTagClick, uriHandler) {
        LinkInteractionListener { link ->
            val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
            if (url.startsWith(PlainTagLinkPrefix)) {
                onTagClick(Uri.decode(url.removePrefix(PlainTagLinkPrefix)))
            } else {
                uriHandler.openUri(url)
            }
        }
    }
    val annotatedText = remember(text, tagStyle, linkListener) {
        buildAnnotatedString {
            var cursor = 0
            findCustomTagMatches(text).forEach { match ->
                val start = match.range.first
                val endExclusive = match.range.last + 1
                if (start > cursor) {
                    append(text.substring(cursor, start))
                }
                val tag = getCustomTagName(match)
                withLink(
                    LinkAnnotation.Url(
                        url = PlainTagLinkPrefix + Uri.encode(tag),
                        styles = tagStyle,
                        linkInteractionListener = linkListener,
                    )
                ) {
                    append("\u2009")
                    append(match.value)
                    append("\u2009")
                }
                cursor = endExclusive
            }
            if (cursor < text.length) {
                append(text.substring(cursor))
            }
        }
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyLarge,
        color = colors.textPrimary,
    )
}

internal fun requiresRichMarkdown(text: String): Boolean {
    if (MarkdownLinePrefix.containsMatchIn(text) || MarkdownEmphasisPattern.containsMatchIn(text)) {
        return true
    }
    return MarkdownInlineMarkers.any(text::contains)
}

private const val PREVIEW_UNBREAKABLE_COST = 100
private const val PlainTagLinkPrefix = "gnome://plain-tag/"
private val MarkdownLinePrefix = Regex("(?m)^\\s*(?:#{1,6}\\s|[-+*]\\s|\\d+[.)]\\s|>|~~~)")
private val MarkdownEmphasisPattern = Regex("(?:^|[\\s(])(?:\\*[^*\\n]+\\*|_[^_\\n]+_)")
private val MarkdownInlineMarkers = listOf(
    "http://",
    "https://",
    "**",
    "__",
    "~~",
    "\u0060",
    "![",
    "](",
    "[ ]",
    "[x]",
    "[X]",
    "<",
    "|",
    "\\",
)
private enum class PreviewAppendKind {
    NONE,
    TEXT,
    UNBREAKABLE
}

fun extractPreviewContent(markdownText: String, maxLength: Int = 220): Pair<String, Boolean> {
    if (markdownText.length <= maxLength) {
        return Pair(markdownText, false)
    }

    if (!requiresRichMarkdown(markdownText)) {
        var endIndex = maxLength.coerceAtMost(markdownText.length)
        if (endIndex > 0 && Character.isHighSurrogate(markdownText[endIndex - 1])) {
            endIndex -= 1
        }
        return Pair(markdownText.substring(0, endIndex).trimEnd() + "…", true)
    }

    val node = MarkdownParser(GFMFlavourDescriptor()).parse(
        MarkdownElementTypes.MARKDOWN_FILE,
        markdownText,
        true
    )

    val result = StringBuilder()
    var remainingLength = maxLength
    var truncated = false
    var lastAppendKind = PreviewAppendKind.NONE

    fun appendNodeText(child: ASTNode): Boolean {
        if (remainingLength <= 0) {
            truncated = true
            return false
        }
        val content = markdownText.substring(child.startOffset, child.endOffset)
        if (content.isEmpty()) {
            return true
        }
        if (content.length <= remainingLength) {
            result.append(content)
            remainingLength -= content.length
            lastAppendKind = PreviewAppendKind.TEXT
            return true
        }
        result.append(content.take(remainingLength))
        remainingLength = 0
        truncated = true
        lastAppendKind = PreviewAppendKind.TEXT
        return false
    }

    fun appendUnbreakableNode(child: ASTNode): Boolean {
        if (remainingLength < PREVIEW_UNBREAKABLE_COST) {
            truncated = true
            return false
        }
        result.append(markdownText.substring(child.startOffset, child.endOffset))
        remainingLength -= PREVIEW_UNBREAKABLE_COST
        lastAppendKind = PreviewAppendKind.UNBREAKABLE
        return true
    }

    lateinit var extractNodeContent: (ASTNode) -> Boolean
    lateinit var extractBlockContent: (ASTNode) -> Boolean

    extractNodeContent = { child ->
        if (isUnbreakablePreviewNode(child)) {
            appendUnbreakableNode(child)
        } else if (child.children.isEmpty()) {
            appendNodeText(child)
        } else {
            var allSuccess = true
            for (grandChild in child.children) {
                val success = if (isBreakablePreviewBlock(grandChild.type)) {
                    extractBlockContent(grandChild)
                } else {
                    extractNodeContent(grandChild)
                }
                if (!success) {
                    allSuccess = false
                    break
                }
            }
            allSuccess
        }
    }

    extractBlockContent = { child ->
        var allSuccess = true
        val isParagraph = child.type == MarkdownElementTypes.PARAGRAPH
        for (grandChild in child.children) {
            val success = if (isParagraph) {
                extractNodeContent(grandChild)
            } else if (isBreakablePreviewBlock(grandChild.type)) {
                extractBlockContent(grandChild)
            } else if (isPreviewWhitespaceToken(grandChild) || grandChild.children.isEmpty()) {
                appendNodeText(grandChild)
            } else {
                appendUnbreakableNode(grandChild)
            }
            if (!success) {
                allSuccess = false
                break
            }
        }
        allSuccess
    }

    for (child in node.children) {
        val success = if (isBreakablePreviewBlock(child.type)) {
            extractBlockContent(child)
        } else if (isPreviewWhitespaceToken(child)) {
            appendNodeText(child)
        } else {
            appendUnbreakableNode(child)
        }
        if (!success) {
            break
        }
    }

    if (truncated && lastAppendKind == PreviewAppendKind.TEXT) {
        val preview = result.toString().trimEnd()
        val withEllipsis = if (preview.endsWith("…")) preview else "$preview…"
        return Pair(withEllipsis, true)
    }

    return Pair(result.toString(), truncated)
}

private fun isBreakablePreviewBlock(type: IElementType): Boolean {
    return type == MarkdownElementTypes.MARKDOWN_FILE ||
        type == MarkdownElementTypes.PARAGRAPH ||
        type == MarkdownElementTypes.LIST_ITEM ||
        type == MarkdownElementTypes.BLOCK_QUOTE ||
        type == MarkdownElementTypes.ORDERED_LIST ||
        type == MarkdownElementTypes.UNORDERED_LIST
}

private fun isUnbreakablePreviewNode(node: ASTNode): Boolean {
    return node.type == MarkdownElementTypes.IMAGE ||
        node.type == MarkdownElementTypes.CODE_BLOCK ||
        node.type == MarkdownElementTypes.CODE_FENCE ||
        node.type == GFMElementTypes.TABLE ||
        node.type == MarkdownElementTypes.ATX_1 ||
        node.type == MarkdownElementTypes.ATX_2 ||
        node.type == MarkdownElementTypes.ATX_3 ||
        node.type == MarkdownElementTypes.ATX_4 ||
        node.type == MarkdownElementTypes.ATX_5 ||
        node.type == MarkdownElementTypes.ATX_6 ||
        node.type == MarkdownElementTypes.SETEXT_1 ||
        node.type == MarkdownElementTypes.SETEXT_2 ||
        node.type == MarkdownTokenTypes.HORIZONTAL_RULE ||
        node.type == MarkdownElementTypes.LINK_DEFINITION ||
        node.type.toString().contains("HTML")
}

private fun isPreviewWhitespaceToken(node: ASTNode): Boolean {
    return node.type == MarkdownTokenTypes.EOL || node.type == MarkdownTokenTypes.WHITE_SPACE
}

@Composable
fun MemoResourceContent(memo: MemoRepresentable) {
    val cols = 3
    val context = LocalContext.current
    val imageList = memo.resources.filter { it.mimeType?.startsWith("image/") == true }
    val imageUrls = remember(imageList) {
        imageList.map { resource -> resource.localUri ?: resource.uri }
    }
    if (imageList.isNotEmpty()) {
        val rows = ceil(imageList.size.toFloat() / cols).toInt()
        for (rowIndex in 0 until rows) {
            Row {
                for (colIndex in 0 until cols) {
                    val index = rowIndex * cols + colIndex
                    if (index < imageList.size) {
                        Box(modifier = Modifier.fillMaxWidth(1f / (cols - colIndex))) {
                            MemoImage(
                                url = imageList[index].localUri ?: imageList[index].uri,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                resourceIdentifier = (imageList[index] as? ResourceEntity)?.identifier,
                                onClick = {
                                    context.startActivity(
                                        Intent(context, MediaViewerActivity::class.java).apply {
                                            putExtra(MediaViewerActivity.EXTRA_IMAGE_URLS, imageUrls.toTypedArray())
                                            putExtra(MediaViewerActivity.EXTRA_INITIAL_INDEX, index)
                                            putExtra(MediaViewerActivity.EXTRA_CAPTION, memo.content)
                                        }
                                    )
                                }
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.fillMaxWidth(1f / cols))
                    }
                }
            }
        }
    }
    memo.resources.filterNot { it.mimeType?.startsWith("image/") == true }.forEach { resource ->
        Attachment(resource)
    }
}
