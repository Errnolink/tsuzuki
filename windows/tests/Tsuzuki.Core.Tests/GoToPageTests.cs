using Tsuzuki.Core.Reader;
using Xunit;

namespace Tsuzuki.Core.Tests;

public class GoToPageTests
{
    [Fact]
    public void TypedPageNumberJumpsToThatPage()
    {
        Assert.Equal(11, GoToPage.Parse("12", 48));
    }

    [Fact]
    public void PageWithTotalJumpsToThePageBeforeTheSlash()
    {
        Assert.Equal(11, GoToPage.Parse("12/48", 48));
        Assert.Equal(11, GoToPage.Parse(" 12 / 48 ", 48));
    }

    [Fact]
    public void NumbersOutsideTheChapterLandOnTheFirstOrLastPage()
    {
        Assert.Equal(47, GoToPage.Parse("120", 48));
        Assert.Equal(0, GoToPage.Parse("0", 48));
    }

    [Fact]
    public void TextThatIsNotAPageNumberDoesNothing()
    {
        Assert.Null(GoToPage.Parse("", 48));
        Assert.Null(GoToPage.Parse("abc", 48));
        Assert.Null(GoToPage.Parse("/48", 48));
        Assert.Null(GoToPage.Parse("-3", 48));
    }
}
