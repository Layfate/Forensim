-- Security advisor (lint 0028): two SECURITY DEFINER helpers were callable without signing in via
-- /rest/v1/rpc. Every policy that uses them is `to authenticated`, so signed-out callers never need
-- them. Functions get EXECUTE for PUBLIC by default, so revoke it from PUBLIC and anon.

revoke execute on function public.can_chat(uuid) from public, anon;
revoke execute on function public.can_manage_pdf_folder(text) from public, anon;
grant execute on function public.can_chat(uuid) to authenticated, service_role;
grant execute on function public.can_manage_pdf_folder(text) to authenticated, service_role;
