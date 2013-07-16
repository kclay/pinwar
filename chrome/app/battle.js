(function (w, d) {

    function even(value) {
        return value + (value % 2);
    }

    var PlayerView = Backbone.View.extend({
        points: 0,
        sectionSpacing: 4,
        _reversed: false,
        scaledCanvasSize: null,
        canvasSize: {
            defaultWidth: 562,
            defaultHeight: 124,
            width: 99,
            height: 88.57
        },
        sections: [
            {
                baseColor: "#ecdfce",
                pointsColor: "#c41b24",
                width: 199,
                height: 42
            },
            {
                baseColor: "#ddcdb8",
                pointsColor: "#ae1820",
                width: 116,
                height: 66
            },
            {
                baseColor: "#ddcdb8",
                pointsColor: "#90141a",
                width: 117,
                height: 93
            },
            {
                baseColor: "#ccbaa2",
                pointsColor: "#79070d",
                width: 118,
                height: 124
            }
        ],
        initialize: function (o) {
            this._reversed = o.reversed
            this._points = o.points;
            this._score = 0;
            this._pointsFactor = 0;

            this.$(".likeus-name").text(o.name);
            this.$score = this.$('.likeus-score');
            this.$canvas = this.$(".likeus-progress")
            this.base = new createjs.Shape();
            this.base.name = "base";
            this.points = new createjs.Shape();
            this.points.name = "points"
            this.mask = new createjs.Shape()
            this.mask.name = "mask";
            this.points.mask = this.mask;
            this.totalWidth = 0;

            this.canvas = this.$canvas[0];


            this.stage = new createjs.Stage(this.canvas);
            this.stage.addChild(this.base, this.points);

        },
        _formatPoints: function (x) {
            var parts = x.toString().split(".");
            return parts[0].replace(/\B(?=(\d{3})+(?=$))/g, ",") + (parts[1] ? "." + parts[1] : "");
        },
        addToScore: function (points) {


            var score = this._score + points;

            if (score >= this._points) {
                if (this._score == this._points)
                    return true;

                score = this._points;


            }

            this._score = score;
            var width = this._pointsFactor * this._score;
            // add commas
            var value = this._score.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
            this.$score.text(value + " pts");
            var g = this.mask.graphics;

            var size = this.scaledCanvasSize;


            g.clear();
            g.beginFill("red");
            g.drawRect(0, 0, width, size.height);
            g.endFill();

            this.stage.update();
        },
        render: function () {


            var viewWidth = this.$el.width();
            var viewHeight = this.$el.height();
            console.log(viewWidth + "x" + viewHeight);
            var canvasSize = this.scaledCanvasSize = {
                width: (viewWidth / 100) * this.canvasSize.width,
                height: (viewHeight / 100) * this.canvasSize.height
            }
            this.$canvas.attr(canvasSize)
            var stage = this.stage;
            var baseShape, pointsShape
            var x = 0;
            var y = 0;

            var spacing = this.sectionSpacing

            var prev;
            var sections = this.sections;

            var scaledWidth;
            var factors = {
                width: canvasSize.width / this.canvasSize.defaultWidth
            }
            var self = this;
            var ctx = this.canvas.getContext("2d");
            ctx.lineCap = "round";
            ctx.webkitImageSmoothingEnabled = true;

            var base = this.base, points = this.points;
            $(sections).each(function (index, section) {

                scaledWidth = even(section.width * factors.width);
                y = even(canvasSize.height - section.height)
                self._drawSection(base, x, y, section.baseColor, scaledWidth, index, section);
                self._drawSection(points, x, y, section.pointsColor, scaledWidth, index, section);


                x += even(spacing + scaledWidth);

            })
            this.totalWidth = x;
            this._pointsFactor = x / this._points;

            // this.points.mask = this.mask;
            //stage.addChild(this.mask);
            stage.update();
            return this;
        },
        _drawSection: function (shape, x, y, color, width, index, section) {
            var g = shape.graphics;

            g.beginFill(color)

            width = even(width)
            var height = even(section.height)
            var prev = this.sections[!index ? 0 : index - 1];
            var adjacentHeight = even(!index ? section.height : section.height - prev.height);

            g.moveTo(x, y + even(prev.height));
            g.lineTo(x, y + adjacentHeight);
            g.lineTo(x + width, y);
            g.lineTo(x + width, y + height)
            g.lineTo(x, y + height)

            g.endFill();
            return
        }

    })
    var BattleView = Backbone.View.extend({
        _tick: 0,
        _score: false,
        initialize: function (o) {
            this.left = new PlayerView({
                el: "#likeus-profile-left",
                points: o.points
            }).render();
            this.right = new PlayerView({
                el: "#likeus-profile-right",
                points: o.points,
                reversed: true
            }).render();


            createjs.Ticker.addEventListener("tick", _.bind(this._onTick, this));
        },
        _onTick: function () {

            var self = this;
            var left_won = this.left.addToScore(range(10, 100));
            var right_one = this.right.addToScore(range(10, 200));

            if (left_won || right_one) {
                return createjs.Ticker.removeEventListener("tick");
            }
            if (!this._score) {
                if (!(self._tick % 10)) {
                    self._score = true;
                    $("#likeus-battle").addClass("score");
                    setTimeout(function () {
                        self._score = false;
                        $("#likeus-battle").removeClass("score");
                    }, 1000)

                } else {
                    self._tick++;
                }
            }


        }
    })
    var $ = w.$;


    $.get(likeus_path("template/battle/tpl.html"), function (html) {

        var links_regx = /<link href="([^"]+)"/g;
        var link;

        var preloaded = 0;

        var links = [];

        $(".Header").before(html);
        $(function () {
            new BattleView({
                points: 10000
            });
        })
        /*function loaded() {
         preloaded++;
         if (preloaded == links.length) {


         }
         }



         while (link = links_regx.exec(html)) {
         links.push(link[1])
         }
         for (var i in links) {
         $.get(links[i], function () {
         loaded();
         })
         }    */


    })

    function range(min, max) {
        return Math.floor(Math.random() * (max - min + 1)) + min;
    }
})(window, document);