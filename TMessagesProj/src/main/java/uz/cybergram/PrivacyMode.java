package uz.cybergram;

import android.content.Context;

import org.telegram.messenger.ContactsController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PrivacyControlActivity;
import org.telegram.ui.PrivacySettingsActivity;

/**
 * "👻 Maxfiylik rejimi": Telegram'ning RASMIY maxfiylik sozlamalarini bir joyda ochadi.
 * Hech qanday holat soxtalashtirilmaydi — bu Telegram qoidalariga mos.
 */
public final class PrivacyMode {

    private PrivacyMode() {
    }

    public static void show(final Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(L.t("👻 Maxfiylik rejimi", "👻 Режим приватности"));
        builder.setItems(new CharSequence[]{
                L.t("ℹ️ Bu qanday ishlaydi?", "ℹ️ Как это работает?"),
                L.t("👁 Onlayn va oxirgi marta (o'qilgan vaqt ham shu yerda)", "👁 В сети и последний визит (здесь же время прочтения)"),
                L.t("🖼 Profil rasmi", "🖼 Фото профиля"),
                L.t("📱 Telefon raqami", "📱 Номер телефона"),
                L.t("↪️ Xabarlarimni ulashganda nomim", "↪️ Ссылка на меня при пересылке"),
                L.t("👥 Meni guruhlarga qo'shish", "👥 Добавление меня в группы"),
                L.t("📞 Qo'ng'iroqlar", "📞 Звонки"),
                L.t("🔒 Barcha maxfiylik sozlamalari", "🔒 Все настройки приватности")
        }, (dialog, which) -> {
            switch (which) {
                case 0:
                    explain(context);
                    break;
                case 1:
                    open(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_LASTSEEN));
                    break;
                case 2:
                    open(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_PHOTO));
                    break;
                case 3:
                    open(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_PHONE));
                    break;
                case 4:
                    open(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_FORWARDS));
                    break;
                case 5:
                    open(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_INVITE));
                    break;
                case 6:
                    open(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_CALLS));
                    break;
                default:
                    open(new PrivacySettingsActivity());
                    break;
            }
        });
        builder.setPositiveButton(L.t("Yopish", "Закрыть"), (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private static void explain(final Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(L.t("👻 Maxfiylik rejimi", "👻 Режим приватности"));
        builder.setMessage(
                L.t("Bu yerda Telegram'ning rasmiy maxfiylik sozlamalari bir joyga yig'ilgan.\n\n", "Здесь собраны официальные настройки приватности Telegram.\n\n")
                        + L.t("• \"Onlayn va oxirgi marta\"ni \"Hech kim\" qilsangiz, boshqalar sizni qachon onlayn bo'lganingizni ko'rmaydi.\n", "• Если для «В сети и последний визит» выбрать «Никто», другие не увидят, когда вы были в сети.\n")
                        + L.t("• O'sha sahifada \"o'qilgan vaqtni yashirish\" ham bor.\n", "• На той же странице можно скрыть время прочтения.\n")
                        + L.t("• Diqqat: Telegram qoidasi bo'yicha, siz yashirgan narsani siz ham boshqalarda ko'rmaysiz.\n\n", "• Внимание: по правилам Telegram то, что вы скрываете, вы не увидите и у других.\n\n")
                        + L.t("Firibgarlardan himoya uchun maslahat: telefon raqamingizni \"Hech kim\", ", "Совет для защиты от мошенников: для номера телефона выберите «Никто», ")
                        + L.t("guruhlarga qo'shishni \"Kontaktlarim\" qilib qo'ying.\n\n", "а для добавления в группы — «Мои контакты».\n\n")
                        + L.t("CyberGram holatlarni soxtalashtirmaydi — bu Telegram qoidalariga zid va akkauntingiz uchun xavfli.", "CyberGram не подделывает статусы — это нарушает правила Telegram и опасно для вашего аккаунта."));
        builder.setPositiveButton(L.t("Tushunarli", "Понятно"), (dialog, which) -> show(context));
        builder.show();
    }

    private static void open(BaseFragment fragment) {
        BaseFragment last = LaunchActivity.getSafeLastFragment();
        if (last != null) {
            last.presentFragment(fragment);
        }
    }
}