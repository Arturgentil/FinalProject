function serializeUser(row, extra = {}) {
  return {
    id: row.id,
    name: row.name,
    username: row.username,
    email: row.email,
    birthDate: row.birth_date || '',
    weight: Number(row.weight) || 0,
    height: Number(row.height) || 0,
    bio: row.bio || '',
    avatarUrl: row.avatar_url || '',
    emailVerified: row.email_verified !== false,
    publicProfile: row.public_profile !== false,
    commentPolicy: row.comment_policy || 'all',
    autoShare: row.auto_share === true,
    notifGoal: row.notif_goal !== false,
    notifRecord: row.notif_record !== false,
    notifSocial: row.notif_social !== false,
    following: Number(extra.following || 0),
    followers: Number(extra.followers || 0),
    activeDays: Number(extra.activeDays || 0),
    totalSteps: Number(row.total_steps) || 0,
    totalEnergy: Number(row.total_energy) || 0,
    recordSteps: Number(row.record_steps) || 0,
    recordEnergy: Number(row.record_energy) || 0,
    shoeCustomization: {
      upper: row.shoe_upper,
      sole: row.shoe_sole,
      laces: row.shoe_laces,
      details: row.shoe_details,
      theme: row.shoe_theme,
    },
  };
}

module.exports = { serializeUser };
