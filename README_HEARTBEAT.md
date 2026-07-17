# Arquitetura de Heartbeat - Vision Central

Este documento descreve o funcionamento do sistema de monitoramento de status Online/Offline do projeto.

## Funcionamento

A lógica foi centralizada no **Supabase** para garantir que o estado do dispositivo seja confiável, mesmo que o aplicativo Android seja encerrado abruptamente ou perca a conexão.

### 1. Emissor (Android)
O aplicativo Android atua apenas como um emissor. A cada 10 segundos, ele envia um sinal para o banco de dados:
- Atualiza `ultima_conexao` com o timestamp UTC atual.
- O campo `status` é enviado como 'Online', mas o banco de dados reforça isso via trigger.

### 2. Automação (Supabase)
Foram implementados três mecanismos no banco de dados:

- **Trigger (`tr_ensure_online_on_heartbeat`)**: Sempre que o campo `ultima_conexao` é atualizado na tabela `tvs`, o banco define automaticamente o `status` como `'Online'`. Isso remove a dependência de lógica complexa no cliente.
- **Função (`handle_tv_timeouts`)**: Analisa a tabela `tvs` em busca de dispositivos que não enviam sinal há mais de **30 segundos**. Estes são marcados como `'Offline'`.
- **Auditoria (`heartbeat_logs`)**: Toda vez que um dispositivo muda de 'Online' para 'Offline' através do sistema automático, um registro é inserido nesta tabela para fins de suporte e monitoramento.

### 3. Agendamento (Cron)
Para que a limpeza ocorra automaticamente, utilizamos a extensão `pg_cron` do Supabase.
- O job `handle-tv-heartbeat-timeouts` está configurado para rodar a cada **1 minuto** (limite padrão do pg_cron).
- Caso o `pg_cron` não esteja disponível no seu ambiente, você pode acionar a função `handle_tv_timeouts()` através de uma Edge Function ou um worker externo.

## Como Aplicar

1. Vá ao painel do **Supabase**.
2. Navegue até **Database -> Extensions** e certifique-se de que `pg_cron` está habilitado.
3. Execute o script contido em `supabase/migrations/20240717000001_heartbeat_logic.sql` no Editor SQL ou utilize a CLI do Supabase para aplicar a migração.

## Benefícios
- **Confiabilidade**: O status 'Offline' não depende de um evento `onClose` no Android.
- **Auditoria**: Histórico completo de quedas de conexão.
- **Simplicidade**: O código do aplicativo fica mais limpo e focado apenas na reprodução de mídia.
