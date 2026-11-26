package fr.hykiuidustries.createboxed;

/**
 * Single source of truth for the strings that must stay in sync with the language file
 * and with the serialized fields. Renaming a key or an NBT field only requires one edit here.
 */
public final class Keys {
    private Keys() {
    }

    /**
     * Translation keys present in {@code assets/createboxed/lang/en_us.json}.
     */
    public static final class Lang {
        public static final String ADMIN_INVITE_RECEIVED = "createboxed.admin.invite_received";
        public static final String ADMIN_INVITE_SENT = "createboxed.admin.invite_sent";
        public static final String ADMIN_KICK_DONE = "createboxed.admin.kick_done";
        public static final String ADMIN_LEADER_CHANGED = "createboxed.admin.leader_changed";
        public static final String ADMIN_LIST_ENTRY = "createboxed.admin.list.entry";
        public static final String ADMIN_LIST_HEADER = "createboxed.admin.list.header";
        public static final String ADMIN_NO_TEAMS = "createboxed.admin.no_teams";
        public static final String ADMIN_RENAMED = "createboxed.admin.renamed";
        public static final String ADMIN_TELEPORTED = "createboxed.admin.teleported";
        public static final String ADMIN_VISIT_SENT = "createboxed.admin.visit_sent";
        public static final String ADMIN_VISIT_TARGET = "createboxed.admin.visit_target";
        public static final String INFO_HOVER_MESSAGE = "createboxed.info.hover.message";
        public static final String INFO_HOVER_VISIT = "createboxed.info.hover.visit";
        public static final String INFO_LABEL_LEADER = "createboxed.info.label.leader";
        public static final String INFO_LABEL_MEMBERS = "createboxed.info.label.members";
        public static final String INFO_LABEL_SLOT = "createboxed.info.label.slot";
        public static final String INFO_LABEL_TEAM = "createboxed.info.label.team";
        public static final String INFO_PROGRESS = "createboxed.info.progress";
        public static final String INFO_STATUS_DELETING = "createboxed.info.status.deleting";
        public static final String INFO_STATUS_DONE = "createboxed.info.status.done";
        public static final String INFO_STATUS_GENERATING = "createboxed.info.status.generating";
        public static final String SLOT_ALREADY_INSIDE = "createboxed.slot.already_inside";
        public static final String SLOT_BUSY = "createboxed.slot.busy";
        public static final String SLOT_DELETE_CONFIRM = "createboxed.slot.delete_confirm";
        public static final String SLOT_DELETE_STARTED = "createboxed.slot.delete_started";
        public static final String SLOT_DONE = "createboxed.slot.done";
        public static final String SLOT_GENERATING = "createboxed.slot.generating";
        public static final String SLOT_NO_SLOT = "createboxed.slot.no_slot";
        public static final String SLOT_NONE = "createboxed.slot.none";
        public static final String SLOT_NOT_READY = "createboxed.slot.not_ready";
        public static final String SLOT_PLAYER_ONLY = "createboxed.slot.player_only";
        public static final String SLOT_PROGRESS = "createboxed.slot.progress";
        public static final String SLOT_PROGRESS_OVERLAY = "createboxed.slot.progress_overlay";
        public static final String SLOT_QUEUED = "createboxed.slot.queued";
        public static final String SLOT_READY = "createboxed.slot.ready";
        public static final String SLOT_SERVER_ONLY = "createboxed.slot.server_only";
        public static final String SLOT_SPAWN_ADJUSTED = "createboxed.slot.spawn_adjusted";
        public static final String SLOT_SPAWN_COORDS_REQUIRED = "createboxed.slot.spawn_coords_required";
        public static final String SLOT_SPAWN_OUTSIDE = "createboxed.slot.spawn_outside";
        public static final String SLOT_SPAWN_PLATFORM = "createboxed.slot.spawn_platform";
        public static final String SLOT_SPAWN_SET = "createboxed.slot.spawn_set";
        public static final String SLOT_SPAWN_SET_ADJUSTED = "createboxed.slot.spawn_set_adjusted";
        public static final String SLOT_SPAWN_SET_ADMIN = "createboxed.slot.spawn_set_admin";
        public static final String SLOT_SPAWN_SET_ADMIN_ADJUSTED = "createboxed.slot.spawn_set_admin_adjusted";
        public static final String SLOT_STARTED = "createboxed.slot.started";
        public static final String SLOT_STATUS_INDEX = "createboxed.slot.status.index";
        public static final String SLOT_STATUS_NONE = "createboxed.slot.status.none";
        public static final String SLOT_VISITED = "createboxed.slot.visited";
        public static final String SLOT_VISITS_DISABLED = "createboxed.slot.visits_disabled";
        public static final String TEAM_ALREADY_MEMBER = "createboxed.team.already_member";
        public static final String TEAM_CREATED = "createboxed.team.created";
        public static final String TEAM_DELETE_SLOT_FIRST = "createboxed.team.delete_slot_first";
        public static final String TEAM_DISBANDED = "createboxed.team.disbanded";
        public static final String TEAM_INFO = "createboxed.team.info";
        public static final String TEAM_INVALID_NAME = "createboxed.team.invalid_name";
        public static final String TEAM_INVITE_EXPIRED = "createboxed.team.invite_expired";
        public static final String TEAM_INVITE_RECEIVED = "createboxed.team.invite_received";
        public static final String TEAM_INVITE_SELF = "createboxed.team.invite_self";
        public static final String TEAM_INVITE_SENT = "createboxed.team.invite_sent";
        public static final String TEAM_JOINED = "createboxed.team.joined";
        public static final String TEAM_KICK_DONE = "createboxed.team.kick_done";
        public static final String TEAM_KICK_SELF = "createboxed.team.kick_self";
        public static final String TEAM_KICKED = "createboxed.team.kicked";
        public static final String TEAM_LEAVE_SOLO = "createboxed.team.leave_solo";
        public static final String TEAM_LEFT = "createboxed.team.left";
        public static final String TEAM_MEMBER_NOT_FOUND = "createboxed.team.member_not_found";
        public static final String TEAM_NAME_TAKEN = "createboxed.team.name_taken";
        public static final String TEAM_NO_INVITE = "createboxed.team.no_invite";
        public static final String TEAM_NOT_FOUND = "createboxed.team.not_found";
        public static final String TEAM_NOT_LEADER = "createboxed.team.not_leader";
        public static final String TEAM_PLAYER_NOT_FOUND = "createboxed.team.player_not_found";
        public static final String TEAM_RENAMED = "createboxed.team.renamed";
        private Lang() {
        }
    }

    /**
     * NBT field names used by the persistent SavedData records.
     */
    public static final class Nbt {
        public static final String ANCHORED = "anchored";
        public static final String ANCHOR_X = "anchorX";
        public static final String ANCHOR_Z = "anchorZ";
        public static final String GAP = "gap";
        public static final String NEXT_INDEX = "nextIndex";
        public static final String NEXT_TEAM_ID = "nextTeamId";
        public static final String PLOTS = "plots";
        public static final String TEAMS = "teams";
        public static final String MEMBERSHIPS = "memberships";
        public static final String UUID = "uuid";
        public static final String TEAM_ID = "teamId";
        public static final String ID = "id";
        public static final String NAME = "name";
        public static final String LEADER = "leader";
        public static final String SLOT_INDEX = "slotIndex";
        public static final String MEMBERS = "members";
        public static final String INDEX = "index";
        public static final String CELL_X = "cellX";
        public static final String CELL_Z = "cellZ";
        public static final String ORIGIN_X = "originX";
        public static final String ORIGIN_Z = "originZ";
        public static final String STATUS = "status";
        public static final String NEXT_CHUNK = "nextChunk";
        public static final String SPAWN_POINT = "spawnPoint";
        private Nbt() {
        }
    }
}
