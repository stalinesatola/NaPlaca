package ao.naplaca.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ao.naplaca.app.domain.Match
import ao.naplaca.app.domain.MatchState
import ao.naplaca.app.domain.TeamSide
import ao.naplaca.app.ui.Format
import ao.naplaca.app.ui.theme.LiveRed

@Composable
fun MatchRow(
    match: Match,
    followed: Boolean,
    onClick: () -> Unit,
    onToggleFollow: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusColumn(match, showDate, Modifier.width(64.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TeamLine(match.home, match.state, winner = isWinner(match, home = true))
            TeamLine(match.away, match.state, winner = isWinner(match, home = false))
        }
        if (onToggleFollow != null && match.state != MatchState.POST) {
            IconButton(onClick = onToggleFollow) {
                Icon(
                    if (followed) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone,
                    contentDescription = if (followed) "Deixar de seguir" else "Seguir jogo",
                    tint = if (followed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

private fun isWinner(m: Match, home: Boolean): Boolean {
    if (m.state != MatchState.POST) return false
    val h = m.home.score ?: return false
    val a = m.away.score ?: return false
    return if (home) h > a else a > h
}

@Composable
private fun TeamLine(team: TeamSide, state: MatchState, winner: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(team.logo, size = 22.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            team.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (team.redCards > 0) {
            Box(
                Modifier.padding(horizontal = 4.dp).size(width = 9.dp, height = 12.dp)
                    .clip(RoundedCornerShape(2.dp)).background(LiveRed),
            )
        }
        if (state != MatchState.PRE && team.score != null) {
            Text(
                team.score.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (state == MatchState.LIVE) LiveRed else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
fun StatusColumn(match: Match, showDate: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        when (match.state) {
            MatchState.LIVE -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot()
                    Spacer(Modifier.width(4.dp))
                    Text(match.statusDetail, color = LiveRed, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            MatchState.PRE -> {
                if (showDate) {
                    Text(Format.dateTime(match.startEpochMillis), style = MaterialTheme.typography.labelMedium)
                } else {
                    Text(Format.time(match.startEpochMillis), style = MaterialTheme.typography.labelLarge)
                }
            }
            else -> {
                Text(match.statusDetail, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showDate) {
                    Text(Format.dateTime(match.startEpochMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(modifier.size(8.dp).alpha(alpha).clip(CircleShape).background(LiveRed))
}
