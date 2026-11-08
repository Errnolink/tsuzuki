using Microsoft.UI.Xaml.Automation.Peers;
using Microsoft.UI.Xaml.Controls;

namespace Tsuzuki.App.Navigation;

public partial class TzPage : UserControl
{
    private bool asleep;

    public bool Asleep
    {
        get => asleep;
        set
        {
            if (asleep == value)
            {
                return;
            }
            asleep = value;
            FrameworkElementAutomationPeer.FromElement(this)?.RaiseStructureChangedEvent(AutomationStructureChangeType.ChildrenInvalidated, null);
        }
    }

    protected override AutomationPeer OnCreateAutomationPeer() => new Peer(this);

    private sealed partial class Peer(TzPage owner) : FrameworkElementAutomationPeer(owner)
    {
        protected override IList<AutomationPeer>? GetChildrenCore() => owner.asleep ? null : base.GetChildrenCore();

        protected override bool IsOffscreenCore() => owner.asleep || base.IsOffscreenCore();
    }
}
