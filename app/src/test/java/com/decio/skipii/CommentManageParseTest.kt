package com.decio.skipii

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner edit/delete tokens as YouTube returns them in a signed-in comment toolbar. */
class CommentManageParseTest {
    private val page = JSONObject(
        """
        {
          "onResponseReceivedEndpoints": [{
            "reloadContinuationItemsCommand": {
              "continuationItems": [{
                "commentThreadRenderer": {
                  "commentViewModel": {
                    "commentViewModel": {
                      "commentId": "Ugxthread",
                      "commentKey": "comment-key",
                      "toolbarStateKey": "state-key",
                      "toolbarSurfaceKey": "surface-key"
                    }
                  }
                }
              }]
            }
          }],
          "frameworkUpdates": {
            "entityBatchUpdate": {
              "mutations": [{
                "entityKey": "comment-key",
                "payload": {
                  "commentEntityPayload": {
                    "properties": {
                      "commentId": "Ugxthread",
                      "content": {"content": "A comment of mine"},
                      "publishedTime": "1 second ago"
                    },
                    "author": {
                      "displayName": "@me",
                      "channelId": "UCme",
                      "avatarThumbnailUrl": "https://yt3.ggpht.com/me"
                    },
                    "toolbar": {"replyCount": "0"}
                  }
                }
              }, {
                "entityKey": "state-key",
                "payload": {
                  "engagementToolbarStateEntityPayload": {
                    "likeState": "TOOLBAR_LIKE_STATE_LIKED"
                  }
                }
              }, {
                "entityKey": "surface-key",
                "payload": {
                  "engagementToolbarSurfaceEntityPayload": {
                    "menuCommand": {
                      "innertubeCommand": {
                        "menuEndpoint": {
                          "menu": {
                            "menuRenderer": {
                              "items": [{
                                "menuServiceItemRenderer": {
                                  "text": {"runs": [{"text": "Edit"}]},
                                  "icon": {"iconType": "EDIT"},
                                  "navigationEndpoint": {
                                    "updateCommentDialogEndpoint": {
                                      "dialog": {
                                        "commentDialogRenderer": {
                                          "submitButton": {
                                            "buttonRenderer": {
                                              "command": {
                                                "updateCommentEndpoint": {
                                                  "updateCommentParams": "edit-token"
                                                }
                                              }
                                            }
                                          }
                                        }
                                      }
                                    }
                                  }
                                }
                              }, {
                                "menuServiceItemRenderer": {
                                  "text": {"runs": [{"text": "Delete"}]},
                                  "icon": {"iconType": "DELETE"},
                                  "serviceEndpoint": {
                                    "performCommentActionEndpoint": {
                                      "action": "delete-token"
                                    }
                                  }
                                }
                              }]
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }]
            }
          }
        }
        """.trimIndent(),
    )

    @Test fun ownerMenuOffersEditAndDelete() {
        val parsed = CommentFeed.parsePage(page)
        val comment = parsed.comments.singleOrNull()
        assertNotNull(comment)
        assertEquals("edit-token", comment!!.manage?.editParams)
        assertEquals("delete-token", comment.manage?.deleteAction)
        assertTrue(comment.liked)
    }
}
