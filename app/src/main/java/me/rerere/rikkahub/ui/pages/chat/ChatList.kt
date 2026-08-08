private fun ChatListNormal(
    {
    fun List<LazyListItemInfo>.isAtBottom(): Boolean {
        val lastItem = lastOrNull() ?: return false
        val lastPos = lastItem.offset + lastItem.size
        return lastPos <= state.layoutInfo.viewportEndOffset - 8
    }

    // 聊天选择
    val selectedItems = remember { mutableStateListOf<Uuid>() }
    var selecting by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    // 选中状态自动清空
    LaunchedEffect(selecting) {
        if (!selecting) {
            selectedItems.clear()
        }
    }

    // 自动跟随键盘滚动
    if (settings.displaySetting.enableAutoScroll) {
        // 贴底闩锁：生成期间一旦贴底就持续请求贴底，增长导致短暂离开底部时不丢请求
        var stickToBottom by remember { mutableStateOf(true) }
        LaunchedEffect(state) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo }.collect { visibleItemsInfo ->
                if (!state.isScrollInProgress && loading) {
                    if (visibleItemsInfo.isAtBottom()) {
                        stickToBottom = true
                    } else {
                        stickToBottom = false
                    }
                    if (stickToBottom) {
                        state.requestScrollToItem(conversation.messageNodes.lastIndex + 10)
                    }
                }
            }
        }
    }

    // LazyColumn
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(16.dp) + PaddingValues(bottom = 32.dp + innerPadding.calculateBottomPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = hazeState)
            .padding(top = innerPadding.calculateTopPadding()),
    ) {
        // 尺寸警告
        val sizeInfo = remember { rememberConversationSizeInfo(conversation) }
        if (sizeInfo.showWarning && showSizeWarningDialog) {
            ConversationSizeWarningDialog(
                onDismiss = { showSizeWarningDialog = false },
                sizeInfo = sizeInfo
            )
        }

        // 主要内容
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            // 消息列表
            val displayNodes = remember(conversation.messageNodes) {
                conversation.messageNodes.filter { node ->
                    val msg = node.currentMessage
                    val text = msg.toText().trim()
                    !(msg.role == MessageRole.ASSISTANT && text == "[SKIP]")
                }
            }

            itemsIndexed(
                items = displayNodes,
                key = { index, item -> item.id },
            ) { index, node ->
                Column {
                    ListSelectableItem(
                        key = node.id,
                        onSelectChange = {
                            if (!selectedItems.contains(node.id)) {
                                selectedItems.add(node.id)
                            } else {
                                selectedItems.remove(node.id)
                            }
                        },
                        selectedKeys = selectedItems,
                    )
                    ChatMessage(
                        node = node,
                        assistant = if (conversation.assistantIds.size > 1) {
                            node.currentMessage.modelId?.let { mid ->
                                conversation.assistantIds.first { it != mid }
                            }
                        } else {
                            null
                        },
                        onPublish = if (conversation.assistantIds.size > 1 &&
                            node.currentMessage.modelId != null
                        ) {
                            { assistantId ->
                                conversation.publish(assistantId)
                            }
                        } else {
                            null
                        },
                        onRegenerate = {
                            conversation.regenerate(node.currentMessage.id)
                        },
                        onEdit = {
                            conversation.edit(node.currentMessage.id)
                        },
                        onFork = {
                            conversation.fork(node.currentMessage.id)
                        },
                        onDelete = {
                            conversation.delete(node.currentMessage.id)
                        },
                        onShare = {
                            conversation.share(node.currentMessage.id)
                        },
                        selected = selectedItems.contains(node.id),
                        onUpdate = {
                            conversation.update(it)
                        },
                        onToggleFavorite = {
                            conversation.toggleFavorite(node.currentMessage.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
    // 函数结束
    }