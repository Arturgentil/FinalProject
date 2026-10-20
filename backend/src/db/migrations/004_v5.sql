-- StepVolt - migração V5
-- Foto de perfil guardada no banco e preferências de privacidade que o
-- servidor de fato respeita. Segura para rodar mais de uma vez.

ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_data BYTEA;
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_mime VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_updated_at TIMESTAMPTZ;

ALTER TABLE users ADD COLUMN IF NOT EXISTS public_profile BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN IF NOT EXISTS comment_policy VARCHAR(10) NOT NULL DEFAULT 'all';
ALTER TABLE users ADD COLUMN IF NOT EXISTS auto_share BOOLEAN NOT NULL DEFAULT false;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'users_comment_policy_chk') THEN
    ALTER TABLE users ADD CONSTRAINT users_comment_policy_chk
      CHECK (comment_policy IN ('all', 'followers', 'none'));
  END IF;
END $$;

-- Por que um token de renovação foi invalidado: 'rotated' (já trocado por um
-- novo) ou 'revoked' (logout ou troca de senha). Só o reuso de um token
-- 'rotated' indica cópia/roubo; um aparelho antigo com token 'revoked' apenas
-- recebe 401, sem derrubar as outras sessões.
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS revoke_reason VARCHAR(10);

-- Quais avisos a pessoa quer receber na tela de Notificações. O servidor só
-- cria o aviso se a preferência estiver ligada (antes eram só interruptores
-- guardados no celular, sem efeito).
ALTER TABLE users ADD COLUMN IF NOT EXISTS notif_goal BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN IF NOT EXISTS notif_record BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN IF NOT EXISTS notif_social BOOLEAN NOT NULL DEFAULT true;
